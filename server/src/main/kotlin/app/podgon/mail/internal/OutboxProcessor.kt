package app.podgon.mail.internal

import app.podgon.mail.MailSender
import app.podgon.mail.MailTemplate
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration

/**
 * Фоновая отправка писем из outbox с повторами и экспоненциальной паузой.
 * В логи не попадают адреса и содержимое писем — только идентификатор записи.
 */
@Service
class OutboxProcessor(
    private val repository: OutboxRepository,
    private val sender: MailSender,
    private val renderer: MailRenderer,
    private val props: MailProperties,
    private val json: JsonMapper,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(OutboxProcessor::class.java)

    @Scheduled(fixedDelayString = "\${podgon.mail.poll-interval:2s}")
    fun poll() {
        if (props.pollEnabled) processBatch()
    }

    /** Обрабатывает до [MailProperties.batchSize] писем; возвращает число обработанных. */
    fun processBatch(): Int {
        var processed = 0
        while (processed < props.batchSize && processOne()) processed++
        return processed
    }

    private fun processOne(): Boolean =
        tx.execute {
            val message = repository.claimNext(clock.instant()) ?: return@execute false
            try {
                val params: Map<String, String> =
                    json.readValue(
                        message.payloadJson,
                        object : TypeReference<Map<String, String>>() {},
                    )
                val mail = renderer.render(MailTemplate.valueOf(message.template), message.locale, params)
                sender.send(message.to, mail.subject, mail.body)
                repository.markSent(message.id, clock.instant())
            } catch (e: Exception) {
                val attempt = message.attempts + 1
                log.warn("Не удалось отправить письмо {} (попытка {}): {}", message.id, attempt, e.javaClass.simpleName)
                if (attempt >= props.maxAttempts) {
                    repository.markFailed(message.id)
                } else {
                    val pause = props.retryBackoff.multipliedBy(1L shl (attempt - 1).coerceAtMost(10))
                    repository.markRetry(message.id, clock.instant().plus(pause))
                }
            }
            true
        } ?: false

    /** Ночная очистка: удаляет старые обработанные письма. */
    @Scheduled(cron = "0 30 3 * * *")
    fun scheduledCleanup() {
        cleanup()
    }

    /** Удаляет старые обработанные письма. */
    fun cleanup(retention: Duration = Duration.ofDays(7)): Int =
        repository.deleteFinishedBefore(clock.instant().minus(retention))
}
