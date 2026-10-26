package app.podgon.reviews.domain

import app.podgon.reviews.persistence.ReviewRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/** Ночная чистка общего словаря тегов: теги без рекомендаций не нужны ни поиску, ни подсказкам. */
@Component
class TagCleanup(
    private val reviews: ReviewRepository,
    private val tx: TransactionTemplate,
) {
    @Scheduled(cron = "0 45 3 * * *")
    fun run() {
        tx.executeWithoutResult { reviews.deleteUnusedTags() }
    }
}
