package app.podgon.groups.domain

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.groups.GroupDeleting
import app.podgon.groups.persistence.GroupRepository
import app.podgon.identity.UserDeleting
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

/** Порядок обработчиков [UserDeleting]: группы раньше рекомендаций (они ещё видят публикации пустых групп). */
const val GROUPS_FIRST = 10

/**
 * Снимает членства удаляемого пользователя. Единственный администратор группы с другими участниками
 * удалиться не может (409 `LAST_ADMIN`); группа, где он один, удаляется целиком — её рекомендации остаются личными.
 */
@Component
class AccountMemberships(
    private val groups: GroupRepository,
    private val events: ApplicationEventPublisher,
) {
    @EventListener
    @Order(GROUPS_FIRST)
    fun onUserDeleting(event: UserDeleting) {
        val userId = event.userId
        val blocking = groups.soleAdminGroups(userId)
        if (blocking.isNotEmpty()) throw ApiException(ErrorCode.LAST_ADMIN, HttpStatus.CONFLICT)
        for (groupId in groups.groupsWhereOnlyMember(userId)) {
            if (!groups.lock(groupId)) continue
            events.publishEvent(GroupDeleting(groupId, deleteExclusiveReviews = false))
            groups.delete(groupId)
        }
        groups.deleteMembershipsOf(userId)
    }
}
