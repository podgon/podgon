package app.podgon.reviews.domain

import app.podgon.reviews.ReviewPublications
import app.podgon.reviews.persistence.ReviewRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PublicationsPort(
    private val reviews: ReviewRepository,
) : ReviewPublications {
    override fun count(
        groupId: UUID,
        categoryIds: Collection<UUID>,
    ): Int = reviews.countPublications(groupId, categoryIds)

    override fun countByOthers(
        groupId: UUID,
        categoryIds: Collection<UUID>,
        userId: UUID,
    ): Int = reviews.countPublicationsByOthers(groupId, categoryIds, userId)
}
