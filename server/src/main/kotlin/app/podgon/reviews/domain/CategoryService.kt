package app.podgon.reviews.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.CategoryDeleteImpact
import app.podgon.contract.CategoryInfo
import app.podgon.contract.CreateCategoryRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.MoveCategoryRequest
import app.podgon.contract.UpdateCategoryRequest
import app.podgon.contract.Validation
import app.podgon.groups.GroupAccess
import app.podgon.reviews.ReviewsProperties
import app.podgon.reviews.persistence.CategoryRepository
import app.podgon.reviews.persistence.CategoryRow
import app.podgon.reviews.persistence.ReviewRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

private fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)

private fun conflict(code: ErrorCode) = ApiException(code, HttpStatus.CONFLICT)

private fun CategoryRow.toDto() = CategoryInfo(id.toString(), parentId?.toString(), name, description)

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/**
 * Категории группы: читать может любой участник, менять — администратор (проверка в [GroupAccess]).
 * Изменения дерева сериализуются блокировкой на группу, чтобы проверки цикла, глубины и уникальности
 * имён не гонялись друг с другом.
 */
@Service
class CategoryService(
    private val categories: CategoryRepository,
    private val access: GroupAccess,
    private val reviews: ReviewRepository,
    private val props: ReviewsProperties,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    fun list(
        userId: UUID,
        groupId: UUID,
    ): List<CategoryInfo> {
        access.requireMember(userId, groupId)
        return categories.list(groupId).map { it.toDto() }
    }

    fun create(
        userId: UUID,
        groupId: UUID,
        request: CreateCategoryRequest,
    ): CategoryInfo {
        access.requireAdmin(userId, groupId)
        val parentId = request.parentId?.let { it.toUuidOrNull() ?: throw notFound() }
        val name = request.name.trim()
        val description = request.description.trim()
        validate(name, description)
        return tx.execute {
            categories.lockGroup(groupId)
            if (categories.count(groupId) >= props.categories.maxPerGroup) throw conflict(ErrorCode.LIMIT_EXCEEDED)
            if (parentId != null) {
                categories.find(groupId, parentId) ?: throw notFound()
                if (categories.depthOf(groupId, parentId) + 1 >
                    props.categories.maxDepth
                ) {
                    throw conflict(ErrorCode.CATEGORY_TOO_DEEP)
                }
            }
            if (categories.nameExists(groupId, parentId, name)) throw conflict(ErrorCode.CATEGORY_NAME_TAKEN)
            val id = UUID.randomUUID()
            categories.insert(
                id,
                groupId,
                parentId,
                null,
                name,
                description,
                categories.nextSortOrder(groupId, parentId),
                userId,
                clock.instant(),
            )
            CategoryInfo(id.toString(), parentId?.toString(), name, description)
        }!!
    }

    /** Частичное обновление: `null` — поле не менять. */
    fun update(
        userId: UUID,
        groupId: UUID,
        categoryId: UUID,
        request: UpdateCategoryRequest,
    ): CategoryInfo {
        access.requireAdmin(userId, groupId)
        return tx.execute {
            categories.lockGroup(groupId)
            val current = categories.find(groupId, categoryId) ?: throw notFound()
            val name = request.name?.trim() ?: current.name
            val description = request.description?.trim() ?: current.description
            validate(name, description)
            if (categories.nameExists(groupId, current.parentId, name, exceptId = categoryId)) {
                throw conflict(ErrorCode.CATEGORY_NAME_TAKEN)
            }
            categories.updateText(groupId, categoryId, name, description)
            current.copy(name = name, description = description).toDto()
        }!!
    }

    /** Переносит категорию вместе с поддеревом; `parentId = null` — в корень. */
    fun move(
        userId: UUID,
        groupId: UUID,
        categoryId: UUID,
        request: MoveCategoryRequest,
    ): CategoryInfo {
        access.requireAdmin(userId, groupId)
        val newParentId = request.parentId?.let { it.toUuidOrNull() ?: throw notFound() }
        return tx.execute {
            categories.lockGroup(groupId)
            val current = categories.find(groupId, categoryId) ?: throw notFound()
            if (newParentId == current.parentId) return@execute current.toDto()
            val subtree = categories.subtree(groupId, categoryId)
            if (newParentId != null) {
                categories.find(groupId, newParentId) ?: throw notFound()
                if (subtree.any { it.id == newParentId }) throw conflict(ErrorCode.CATEGORY_CYCLE)
            }
            val parentDepth = newParentId?.let { categories.depthOf(groupId, it) } ?: 0
            if (parentDepth + subtree.maxOf { it.level } >
                props.categories.maxDepth
            ) {
                throw conflict(ErrorCode.CATEGORY_TOO_DEEP)
            }
            if (categories.nameExists(groupId, newParentId, current.name, exceptId = categoryId)) {
                throw conflict(ErrorCode.CATEGORY_NAME_TAKEN)
            }
            categories.updateParent(groupId, categoryId, newParentId, categories.nextSortOrder(groupId, newParentId))
            current.copy(parentId = newParentId).toDto()
        }!!
    }

    fun deleteImpact(
        userId: UUID,
        groupId: UUID,
        categoryId: UUID,
    ): CategoryDeleteImpact {
        access.requireAdmin(userId, groupId)
        return impact(groupId, categoryId, userId)
    }

    /** Удаляет категорию с поддеревом; если оно не пусто, нужно явное подтверждение. */
    fun delete(
        userId: UUID,
        groupId: UUID,
        categoryId: UUID,
        confirm: Boolean,
    ) {
        access.requireAdmin(userId, groupId)
        tx.executeWithoutResult {
            categories.lockGroup(groupId)
            val impact = impact(groupId, categoryId, userId)
            if (impact.foreignPublications > 0) throw conflict(ErrorCode.CATEGORY_HAS_FOREIGN_REVIEWS)
            if (!confirm && (impact.subcategories > 0 || impact.publications > 0)) {
                throw ApiException(ErrorCode.CONFIRMATION_REQUIRED, HttpStatus.BAD_REQUEST)
            }
            categories.delete(groupId, categoryId)
        }
    }

    private fun impact(
        groupId: UUID,
        categoryId: UUID,
        userId: UUID,
    ): CategoryDeleteImpact {
        categories.find(groupId, categoryId) ?: throw notFound()
        val ids = categories.subtree(groupId, categoryId).map { it.id }
        return CategoryDeleteImpact(
            subcategories = ids.size - 1,
            publications = reviews.countPublications(groupId, ids),
            foreignPublications = reviews.countPublicationsByOthers(groupId, ids, userId),
        )
    }

    private fun validate(
        name: String,
        description: String,
    ) {
        val errors =
            listOfNotNull(
                Validation.categoryName(name)?.let { FieldError("name", it) },
                Validation.categoryDescription(description)?.let { FieldError("description", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)
    }
}
