package app.podgon.identity.domain

import app.podgon.identity.persistence.EmailCodeRepository
import app.podgon.identity.persistence.RefreshTokenRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** Ночная очистка просроченных кодов и токенов. */
@Component
class IdentityCleanup(
    private val codes: EmailCodeRepository,
    private val tokens: RefreshTokenRepository,
    private val clock: Clock,
) {
    @Scheduled(cron = "0 15 3 * * *")
    fun run() {
        val now = clock.instant()
        codes.deleteCreatedBefore(now.minus(Duration.ofDays(1)))
        // использованные токены нужны до истечения срока — для обнаружения повторного использования
        tokens.deleteExpiredBefore(now.minus(Duration.ofDays(1)))
    }
}
