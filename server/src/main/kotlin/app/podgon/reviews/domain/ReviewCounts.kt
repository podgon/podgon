package app.podgon.reviews.domain

import app.podgon.groups.GroupReviewCounts
import app.podgon.reviews.persistence.FeedRepository
import org.springframework.stereotype.Service
import java.util.UUID

/** Реализация порта групп: число опубликованных в группе рекомендаций. */
@Service
class ReviewCounts(
    private val feed: FeedRepository,
) : GroupReviewCounts {
    override fun count(groupIds: Collection<UUID>): Map<UUID, Int> = feed.reviewCounts(groupIds)
}
