package app.podgon.identity.domain

import app.podgon.common.ApiException
import app.podgon.common.RateLimitedException
import app.podgon.common.validationFailed
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.Validation
import app.podgon.identity.AuthProperties
import app.podgon.identity.persistence.CodeWindow
import app.podgon.identity.persistence.EmailCodeRepository
import app.podgon.mail.MailQueue
import app.podgon.mail.MailTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Выдача и проверка одноразовых кодов входа (ADR-006). Не зависит от того, существует ли аккаунт. */
@Service
class OtpService(
    private val props: AuthProperties,
    private val codes: EmailCodeRepository,
    private val hasher: CodeHasher,
    private val captcha: CaptchaVerifier,
    private val mail: MailQueue,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    private val random = SecureRandom()

    /**
     * Создаёт код и ставит письмо в очередь. Ответ клиенту одинаков для любого email;
     * лимиты считаются по запросам, а не по наличию аккаунта.
     */
    fun request(
        rawEmail: String,
        captchaToken: String?,
        ip: String?,
        locale: String,
    ) {
        Validation.email(rawEmail)?.let { throw validationFailed(listOf(FieldError("email", it))) }
        val email = Validation.normalizeEmail(rawEmail)
        if (!captcha.verify(captchaToken, ip)) throw ApiException(ErrorCode.CAPTCHA_FAILED, HttpStatus.BAD_REQUEST)
        issue(email, ip, locale, MailTemplate.LOGIN_CODE)
    }

    /**
     * Код подтверждения для уже вошедшего пользователя (например, удаление аккаунта): без капчи, с теми же
     * лимитами по адресу.
     */
    fun requestForSignedIn(
        email: String,
        locale: String,
        template: MailTemplate,
    ) = issue(Validation.normalizeEmail(email), null, locale, template)

    private fun issue(
        email: String,
        ip: String?,
        locale: String,
        template: MailTemplate,
    ) {
        tx.executeWithoutResult {
            codes.lockEmail(email)
            val now = clock.instant()
            enforceLimits(email, ip, now)

            val testAccount = isTestAccount(email)
            val code = if (testAccount) props.testAccount.code else generateCode()
            codes.consumeActive(email, now)
            codes.insert(UUID.randomUUID(), email, hasher.hash(email, code), now, now.plus(props.codeTtl), ip)
            if (!testAccount) {
                mail.enqueue(
                    email,
                    template,
                    locale,
                    mapOf(
                        "code" to code,
                        "ttlMinutes" to props.codeTtl.toMinutes().toString(),
                    ),
                )
            }
        }
    }

    /**
     * Проверяет код. Возвращает `true` и гасит код при совпадении; при ошибке увеличивает счётчик попыток
     * (после лимита код аннулируется). Не бросает исключений внутри транзакции, чтобы счётчик сохранился.
     */
    fun verify(
        rawEmail: String,
        code: String,
    ): Boolean {
        val errors =
            listOfNotNull(
                Validation.email(rawEmail)?.let { FieldError("email", it) },
                Validation.code(code)?.let { FieldError("code", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)
        val email = Validation.normalizeEmail(rawEmail)

        return tx.execute {
            codes.lockEmail(email)
            val now = clock.instant()
            val row = codes.findActiveForUpdate(email, now, props.codeMaxAttempts) ?: return@execute false
            if (hasher.matches(row.codeHmac, email, code)) {
                codes.consume(row.id, now)
                true
            } else {
                codes.incrementAttempts(row.id)
                if (row.attempts + 1 >= props.codeMaxAttempts) codes.consume(row.id, now)
                false
            }
        } ?: false
    }

    private fun enforceLimits(
        email: String,
        ip: String?,
        now: Instant,
    ) {
        codes.latestCreatedAt(email)?.let { last ->
            val wait = Duration.between(now, last.plus(props.resendInterval))
            if (!wait.isNegative && !wait.isZero) throw RateLimitedException(wait.seconds.coerceAtLeast(1))
        }
        val hour = Duration.ofHours(1)
        checkWindow(codes.windowByEmail(email, now.minus(hour)), props.maxCodesPerEmailPerHour, now, hour)
        if (ip != null) checkWindow(codes.windowByIp(ip, now.minus(hour)), props.maxCodesPerIpPerHour, now, hour)
    }

    private fun checkWindow(
        window: CodeWindow,
        limit: Int,
        now: Instant,
        span: Duration,
    ) {
        if (window.count >= limit) {
            val retry = window.oldest?.plus(span)?.let { Duration.between(now, it).seconds } ?: span.seconds
            throw RateLimitedException(retry.coerceAtLeast(1))
        }
    }

    private fun isTestAccount(email: String): Boolean =
        props.testAccount.enabled && Validation.normalizeEmail(props.testAccount.email) == email

    private fun generateCode(): String = "%06d".format(random.nextInt(1_000_000))
}
