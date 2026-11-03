package app.podgon.reviews.domain

import app.podgon.media.MediaKeys
import app.podgon.media.MediaStorage
import app.podgon.reviews.persistence.ReviewRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Ночная чистка фото без ссылок: рекомендации удаляются и пачками (выход с удалением, удаление группы
 * или аккаунта), их файлы убираются здесь. Файлы моложе суток не трогаются: ссылка на них может ещё не записаться.
 */
@Component
class PhotoCleanup(
    private val reviews: ReviewRepository,
    private val storage: MediaStorage,
    private val clock: Clock,
) {
    @Scheduled(cron = "0 0 4 * * *")
    fun run() {
        val old = storage.list(MediaKeys.PHOTOS).filter { it.modifiedAt < clock.instant().minus(GRACE) }
        val ids = old.associateWith { runCatching { UUID.fromString(MediaKeys.idOf(it.key)) }.getOrNull() }
        val used = reviews.usedPhotos(ids.values.filterNotNull())
        ids.filter { (_, id) -> id !in used }.keys.forEach { storage.delete(it.key) }
    }

    private companion object {
        val GRACE: Duration = Duration.ofDays(1)
    }
}
