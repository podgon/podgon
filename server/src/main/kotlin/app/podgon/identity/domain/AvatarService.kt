package app.podgon.identity.domain

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.contract.UserProfile
import app.podgon.identity.persistence.UserRepository
import app.podgon.media.Images
import app.podgon.media.MediaKeys
import app.podgon.media.MediaStorage
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Аватары (этап 8.3.4): квадрат 256 px в JPEG без метаданных. Новый файл пишется до ссылки на него в БД,
 * прежний удаляется сразу после; файлы без ссылок (удалённый аккаунт, сбой между шагами) убирает ночная чистка.
 */
@Service
class AvatarService(
    private val users: UserRepository,
    private val profiles: ProfileService,
    private val storage: MediaStorage,
    private val images: Images,
    private val clock: Clock,
) {
    fun set(
        userId: UUID,
        raw: ByteArray,
    ): UserProfile {
        val current = users.findById(userId) ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        val id = UUID.randomUUID()
        storage.put(MediaKeys.avatar(id.toString()), images.avatar(raw))
        users.setAvatar(userId, id, clock.instant())
        current.avatarId?.let { storage.delete(MediaKeys.avatar(it.toString())) }
        return profiles.get(userId)
    }

    fun remove(userId: UUID): UserProfile {
        val current = users.findById(userId) ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        users.setAvatar(userId, null, clock.instant())
        current.avatarId?.let { storage.delete(MediaKeys.avatar(it.toString())) }
        return profiles.get(userId)
    }

    /** Аватар пользователя [userId] (виден всем вошедшим); нет аватара — 404. */
    fun get(userId: UUID): ByteArray {
        val avatarId = users.findById(userId)?.avatarId ?: throw notFound()
        return storage.get(MediaKeys.avatar(avatarId.toString())) ?: throw notFound()
    }

    /** Ночная чистка: файлы аватаров без ссылок старше суток (свежие могут ещё ждать записи в БД). */
    @Scheduled(cron = "0 15 4 * * *")
    fun cleanup() {
        val old = storage.list(MediaKeys.AVATARS).filter { it.modifiedAt < clock.instant().minus(GRACE) }
        val ids = old.mapNotNull { runCatching { UUID.fromString(MediaKeys.idOf(it.key)) }.getOrNull() }
        val used = users.usedAvatars(ids)
        old
            .filter { f -> runCatching { UUID.fromString(MediaKeys.idOf(f.key)) }.getOrNull() !in used }
            .forEach { storage.delete(it.key) }
    }

    private fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)

    private companion object {
        val GRACE: Duration = Duration.ofDays(1)
    }
}
