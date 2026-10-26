package app.podgon.reviews.domain

import app.podgon.common.validationFailed
import app.podgon.contract.CategoryInfo
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.ShareMode
import app.podgon.contract.SharePreview
import app.podgon.contract.SharePreviewRequest
import app.podgon.contract.ShareRule
import app.podgon.contract.UnmatchedReview
import app.podgon.contract.Validation
import app.podgon.groups.GroupAccess
import app.podgon.groups.InvitationGroups
import app.podgon.groups.MemberJoined
import app.podgon.reviews.persistence.ShareCandidate
import app.podgon.reviews.persistence.ShareSelector
import app.podgon.reviews.persistence.SharingRepository
import app.podgon.reviews.persistence.TargetCategory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** Правило переноса, приведённое к виду для запроса. */
private data class Selection(
    val selector: ShareSelector,
    val categoryIds: Set<UUID>,
    val withSubcategories: Boolean,
    val tags: List<String>,
)

/**
 * Перенос рекомендаций пользователя в новую группу при вступлении: предпросмотр (ничего не меняет) и
 * применение внутри транзакции вступления (обработчик [MemberJoined]).
 */
@Service
class SharingService(
    private val sharing: SharingRepository,
    private val invitations: InvitationGroups,
    private val access: GroupAccess,
    private val clock: Clock,
) {
    fun preview(
        userId: UUID,
        request: SharePreviewRequest,
    ): SharePreview {
        val groupId = invitations.resolve(userId, request.token)
        val categories = sharing.categoriesOf(groupId)
        val candidates = candidates(userId, groupId, request.share)
        val unmatched = candidates.filter { it.targetCategoryId == null }
        val shown = unmatched.take(Validation.SHARE_UNMATCHED_MAX).map(::unmatchedDto)
        return SharePreview(
            total = candidates.size,
            matched = candidates.size - unmatched.size,
            unmatched = shown,
            categories = categories.map(::categoryDto),
        )
    }

    private fun unmatchedDto(c: ShareCandidate) = UnmatchedReview(c.reviewId.toString(), c.title, c.sourceCategory)

    private fun categoryDto(c: TargetCategory) =
        CategoryInfo(c.id.toString(), c.parentId?.toString(), c.name, c.description)

    /**
     * Публикует отобранные рекомендации. Для тех, у кого категория не подобрана, берётся назначение пользователя
     * (если категория принадлежит группе); остальные не переносятся.
     */
    @EventListener
    fun onJoined(event: MemberJoined) {
        val rule = event.share ?: return
        if (rule.mode == ShareMode.NONE) return
        val candidates = candidates(event.userId, event.groupId, rule)
        val groupCategories = sharing.categoriesOf(event.groupId).map { it.id }.toSet()
        val assigned =
            event.assignments
                .mapNotNull { a ->
                    val review = a.reviewId.toUuidOrNull()
                    val category = a.categoryId.toUuidOrNull()
                    if (review != null && category != null && category in groupCategories) review to category else null
                }.toMap()
        val items =
            candidates.mapNotNull { c ->
                val category = c.targetCategoryId ?: assigned[c.reviewId]
                category?.let { c.reviewId to it }
            }
        sharing.publish(event.groupId, items, clock.instant())
    }

    private fun candidates(
        userId: UUID,
        targetGroupId: UUID,
        rule: ShareRule,
    ): List<ShareCandidate> {
        if (rule.mode == ShareMode.NONE) return emptyList()
        val selection = select(rule)
        return sharing.candidates(
            userId = userId,
            targetGroupId = targetGroupId,
            sourceGroupIds = access.memberGroupIds(userId) - targetGroupId,
            selector = selection.selector,
            categoryIds = selection.categoryIds,
            withSubcategories = selection.withSubcategories,
            tags = selection.tags,
        )
    }

    private fun select(rule: ShareRule): Selection {
        val byCategory = rule.mode == ShareMode.CATEGORIES || rule.mode == ShareMode.CATEGORIES_EXCEPT
        val byTag = rule.mode == ShareMode.TAGS || rule.mode == ShareMode.TAGS_EXCEPT
        val errors = mutableListOf<FieldError>()
        val categoryIds = rule.categoryIds.mapNotNull { it.toUuidOrNull() }.toSet()
        val badCategories =
            categoryIds.isEmpty() ||
                categoryIds.size != rule.categoryIds.toSet().size ||
                categoryIds.size > Validation.SHARE_CATEGORIES_MAX
        if (byCategory && badCategories) errors += FieldError("share.categoryIds", FieldErrorReason.INVALID_FORMAT)
        val tags = rule.tags.map(Validation::normalizeTag).distinct()
        if (byTag && (tags.isEmpty() || Validation.tags(tags) != null)) {
            errors += FieldError("share.tags", FieldErrorReason.INVALID_FORMAT)
        }
        if (errors.isNotEmpty()) throw validationFailed(errors)
        val selector =
            when (rule.mode) {
                ShareMode.NONE, ShareMode.ALL -> ShareSelector.ALL
                ShareMode.CATEGORIES -> ShareSelector.IN_CATEGORIES
                ShareMode.CATEGORIES_EXCEPT -> ShareSelector.NOT_IN_CATEGORIES
                ShareMode.TAGS -> ShareSelector.WITH_TAGS
                ShareMode.TAGS_EXCEPT -> ShareSelector.WITHOUT_TAGS
            }
        return Selection(selector, categoryIds, rule.withSubcategories, tags)
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
