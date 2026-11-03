package app.podgon.reviews.domain

import app.podgon.common.validationFailed
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.ShareMode
import app.podgon.contract.SharePreview
import app.podgon.contract.SharePreviewItem
import app.podgon.contract.SharePreviewRequest
import app.podgon.contract.ShareRule
import app.podgon.contract.Validation
import app.podgon.groups.InvitationGroups
import app.podgon.groups.MemberJoined
import app.podgon.reviews.persistence.ShareCandidate
import app.podgon.reviews.persistence.ShareSelector
import app.podgon.reviews.persistence.SharingRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** Правило переноса, приведённое к виду для запроса. */
private data class Selection(
    val selector: ShareSelector,
    val categoryIds: Set<UUID>,
    val tags: List<String>,
)

/**
 * Перенос своих групповых рекомендаций в новую группу при вступлении: предпросмотр (ничего не меняет) и
 * применение внутри транзакции вступления (обработчик [MemberJoined]). Категория — на рекомендации,
 * поэтому сопоставлять ничего не нужно.
 */
@Service
class SharingService(
    private val sharing: SharingRepository,
    private val invitations: InvitationGroups,
    private val clock: Clock,
) {
    fun preview(
        userId: UUID,
        request: SharePreviewRequest,
    ): SharePreview {
        val groupId = invitations.resolve(userId, request.token)
        val candidates = candidates(userId, groupId, request.share)
        return SharePreview(
            total = candidates.size,
            items = candidates.take(Validation.SHARE_PREVIEW_MAX).map(::itemDto),
        )
    }

    private fun itemDto(c: ShareCandidate) = SharePreviewItem(c.reviewId.toString(), c.title, c.categoryId.toString())

    @EventListener
    fun onJoined(event: MemberJoined) {
        val rule = event.share ?: return
        if (rule.mode == ShareMode.NONE) return
        val candidates = candidates(event.userId, event.groupId, rule)
        sharing.publish(event.groupId, candidates.map { it.reviewId }, clock.instant())
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
            selector = selection.selector,
            categoryIds = selection.categoryIds,
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
        return Selection(selector, categoryIds, tags)
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
