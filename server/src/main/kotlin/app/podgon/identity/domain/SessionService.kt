package app.podgon.identity.domain

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.contract.SessionInfo
import app.podgon.contract.TokenPair
import app.podgon.identity.AuthProperties
import app.podgon.identity.persistence.RefreshTokenRepository
import app.podgon.identity.persistence.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID

/**
 * Сессии устройств: пара access/refresh, ротация refresh при каждом использовании
 * и обнаружение повторного использования (отзыв всей цепочки).
 */
@Service
class SessionService(
    private val props: AuthProperties,
    private val tokens: RefreshTokenRepository,
    private val users: UserRepository,
    private val tokenService: TokenService,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    private val random = SecureRandom()

    /** Новая сессия (новое семейство токенов) для пользователя, прошедшего вход. */
    fun start(
        userId: UUID,
        deviceInfo: String?,
    ): TokenPair {
        val now = clock.instant()
        return tx.execute { issue(userId, UUID.randomUUID(), cleanDevice(deviceInfo), now, now) }!!
    }

    /** Меняет refresh-токен на новую пару. Любая проблема с токеном → `INVALID_REFRESH_TOKEN`. */
    fun refresh(rawToken: String): TokenPair {
        val result =
            tx.execute {
                val now = clock.instant()
                val row = tokens.findByHashForUpdate(hash(rawToken)) ?: return@execute null
                when {
                    row.revokedAt != null -> null
                    row.rotatedAt != null -> {
                        // повторное использование уже обменянного токена: возможна кража — закрываем всю сессию
                        tokens.revokeFamily(row.familyId, now)
                        null
                    }
                    !row.expiresAt.isAfter(now) || !row.sessionStartedAt.plus(props.sessionMaxAge).isAfter(now) -> null
                    else -> {
                        val user = users.findById(row.userId)
                        if (user == null || user.status != "ACTIVE") {
                            tokens.revokeFamily(row.familyId, now)
                            null
                        } else {
                            tokens.markRotated(row.id, now)
                            issue(row.userId, row.familyId, row.deviceInfo, row.sessionStartedAt, now)
                        }
                    }
                }
            }
        return result ?: throw ApiException(ErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED)
    }

    /** Завершает сессию по refresh-токену; неизвестный токен не ошибка (идемпотентность). */
    fun logout(rawToken: String) {
        tx.executeWithoutResult {
            tokens.findByHashForUpdate(hash(rawToken))?.let { tokens.revokeFamily(it.familyId, clock.instant()) }
        }
    }

    fun list(
        userId: UUID,
        currentSessionId: UUID,
    ): List<SessionInfo> =
        tokens.findActiveSessions(userId, clock.instant()).map {
            SessionInfo(
                id = it.familyId.toString(),
                deviceInfo = it.deviceInfo,
                createdAt = it.startedAt.toString(),
                lastUsedAt = it.lastUsedAt.toString(),
                current = it.familyId == currentSessionId,
            )
        }

    /** Отзыв сессии пользователем; чужая или несуществующая сессия → 404. */
    fun revoke(
        userId: UUID,
        sessionId: UUID,
    ) {
        if (tokens.revokeUserFamily(userId, sessionId, clock.instant()) == 0) {
            throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
        }
    }

    private fun issue(
        userId: UUID,
        familyId: UUID,
        deviceInfo: String?,
        sessionStartedAt: java.time.Instant,
        now: java.time.Instant,
    ): TokenPair {
        val raw = ByteArray(32).also(random::nextBytes)
        val refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        tokens.insert(
            UUID.randomUUID(),
            userId,
            familyId,
            hash(refresh),
            deviceInfo,
            sessionStartedAt,
            now,
            now.plus(props.refreshTtl),
        )
        return TokenPair(
            accessToken = tokenService.issueAccessToken(userId, familyId),
            refreshToken = refresh,
            expiresIn = props.accessTtl.seconds,
        )
    }

    /** Refresh — 256 бит случайности, поэтому достаточно SHA-256 без соли. */
    private fun hash(rawToken: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))

    private fun cleanDevice(info: String?): String? =
        info
            ?.filterNot {
                it.isISOControl()
            }?.trim()
            ?.take(200)
            ?.ifEmpty { null }
}
