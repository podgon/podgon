package app.podgon.reviews.domain

import app.podgon.contract.AccountDeleteImpact
import app.podgon.contract.GroupDeleteImpact
import app.podgon.contract.LeaveImpact
import app.podgon.groups.GroupAccess
import app.podgon.groups.GroupDeleting
import app.podgon.groups.MemberLeft
import app.podgon.identity.DELETED_USER_ID
import app.podgon.identity.UserDeleting
import app.podgon.reviews.persistence.ReviewRepository
import app.podgon.reviews.persistence.SharingRepository
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.util.UUID

/** Порядок обработчиков [UserDeleting]: сначала группы (членства, пустые группы), затем рекомендации. */
const val REVIEWS_AFTER_GROUPS = 20

/**
 * Что происходит с рекомендациями при выходе из группы, удалении группы и аккаунта: оценка последствий
 * для подтверждения и обработчики событий (выполняются в транзакции того, кто событие опубликовал).
 */
@Service
class LifecycleService(
    private val sharing: SharingRepository,
    private val reviews: ReviewRepository,
    private val access: GroupAccess,
) {
    fun groupDeleteImpact(
        userId: UUID,
        groupId: UUID,
    ): GroupDeleteImpact {
        access.requireAdmin(userId, groupId)
        return GroupDeleteImpact(sharing.countExclusive(groupId), sharing.countGroupPublications(groupId))
    }

    fun leaveImpact(
        userId: UUID,
        groupId: UUID,
    ): LeaveImpact {
        access.requireMember(userId, groupId)
        return LeaveImpact(sharing.countAuthorPublications(groupId, userId))
    }

    fun accountDeleteImpact(userId: UUID): AccountDeleteImpact =
        AccountDeleteImpact(
            reviews = reviews.countByAuthor(userId),
            publishedReviews = sharing.countPublished(userId),
            blockingGroups = access.soleAdminGroups(userId),
        )

    /** Сами рекомендации остаются у автора: в других группах и в личных. */
    @EventListener
    fun onMemberLeft(event: MemberLeft) {
        if (event.deleteContributions) sharing.deleteAuthorPublications(event.groupId, event.userId)
    }

    /** Публикации в группе уйдут каскадом; здесь решается судьба рекомендаций, которые были только в ней. */
    @EventListener
    fun onGroupDeleting(event: GroupDeleting) {
        if (event.deleteExclusiveReviews) sharing.deleteExclusive(event.groupId)
    }

    /**
     * Без удаления: рекомендации с публикациями переходят «удалённому пользователю», личные удаляются
     * (их никто не видит). С удалением — исчезают все, вместе с публикациями и тегами.
     */
    @EventListener
    @Order(REVIEWS_AFTER_GROUPS)
    fun onUserDeleting(event: UserDeleting) {
        if (!event.deleteReviews) sharing.reassignPublished(event.userId, DELETED_USER_ID)
        sharing.deleteAllOf(event.userId)
    }
}
