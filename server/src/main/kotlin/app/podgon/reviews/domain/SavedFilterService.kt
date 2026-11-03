package app.podgon.reviews.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.CreateSavedFilterRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterKind
import app.podgon.contract.SavedFilterOrderRequest
import app.podgon.contract.UpdateSavedFilterRequest
import app.podgon.contract.Validation
import app.podgon.reviews.persistence.SavedFilterRepository
import app.podgon.reviews.persistence.SavedFilterRow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.text.Collator
import java.time.Clock
import java.util.Locale
import java.util.UUID

/**
 * Сохранённые фильтры и закреплённые категории пользователя (И4, И30) — одна таблица. Закреплённые записи
 * идут секциями вверху ленты в порядке пользователя (`position` в пределах вида); закреплённая категория
 * существует, только пока закреплена. Изменения одного пользователя сериализуются блокировкой.
 */
@Service
class SavedFilterService(
    private val repository: SavedFilterRepository,
    private val filters: FeedFilters,
    private val categories: CategoryService,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    private val collator = Collator.getInstance(Locale.forLanguageTag("ru"))

    /** Сначала закреплённые фильтры, затем закреплённые категории (по позиции), затем остальные фильтры по имени. */
    fun list(userId: UUID): List<SavedFilter> {
        val rows = repository.list(userId)
        val pinned = rows.filter { it.inFeed }.sortedWith(compareBy({ it.kind != FILTER_KIND }, { it.position }))
        val rest = rows.filter { !it.inFeed }.sortedWith(compareBy(collator) { it.name.orEmpty() })
        return (pinned + rest).map(::toDto)
    }

    fun create(
        userId: UUID,
        request: CreateSavedFilterRequest,
    ): SavedFilter {
        val draft =
            when (request.kind) {
                SavedFilterKind.FILTER -> filterRow(userId, request)
                SavedFilterKind.CATEGORY -> categoryRow(userId, request)
            }
        return tx.execute {
            repository.lock(userId)
            if (repository.count(userId) >= Validation.SAVED_FILTERS_MAX) {
                throw ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
            }
            if (draft.kind == CATEGORY_KIND &&
                repository.list(userId).any { it.kind == CATEGORY_KIND && it.categoryId == draft.categoryId }
            ) {
                throw ApiException(ErrorCode.ALREADY_PINNED, HttpStatus.CONFLICT)
            }
            val row = draft.copy(position = if (draft.inFeed) repository.nextPosition(userId, draft.kind) else null)
            repository.insert(row, clock.instant())
            toDto(row)
        }!!
    }

    /** Правит только фильтры; закреплённую категорию не правят — её открепляют удалением. */
    fun update(
        userId: UUID,
        id: UUID,
        request: UpdateSavedFilterRequest,
    ): SavedFilter {
        request.name?.let { name -> Validation.savedFilterName(name)?.let { fail("name", it) } }
        request.definition?.let(filters::validate)
        return tx.execute {
            repository.lock(userId)
            val current = repository.find(userId, id) ?: throw notFound()
            if (current.kind != FILTER_KIND) fail("kind", FieldErrorReason.INVALID_FORMAT)
            val inFeed = request.inFeed ?: current.inFeed
            val position =
                when {
                    !inFeed -> null
                    current.inFeed -> current.position
                    else -> repository.nextPosition(userId, FILTER_KIND)
                }
            val row =
                current.copy(
                    name = request.name?.trim() ?: current.name,
                    definition = request.definition?.let(filters::encode) ?: current.definition,
                    inFeed = inFeed,
                    position = position,
                )
            repository.update(row, clock.instant())
            toDto(row)
        }!!
    }

    fun delete(
        userId: UUID,
        id: UUID,
    ) {
        if (!repository.delete(userId, id)) throw notFound()
    }

    /** Полный порядок закреплённых: состав каждого списка должен совпадать с закреплёнными записями своего вида. */
    fun order(
        userId: UUID,
        request: SavedFilterOrderRequest,
    ): List<SavedFilter> {
        tx.executeWithoutResult {
            repository.lock(userId)
            val pinned = repository.list(userId).filter { it.inFeed }
            reorder(pinned, FILTER_KIND, request.filterIds, "filterIds")
            reorder(pinned, CATEGORY_KIND, request.categoryIds, "categoryIds")
        }
        return list(userId)
    }

    private fun reorder(
        pinned: List<SavedFilterRow>,
        kind: String,
        ids: List<String>,
        field: String,
    ) {
        val expected = pinned.filter { it.kind == kind }.map { it.id }.toSet()
        val given = ids.map { runCatching { UUID.fromString(it) }.getOrNull() }
        if (given.any { it == null } || given.size != given.toSet().size || given.toSet() != expected) {
            fail(field, FieldErrorReason.INVALID_FORMAT)
        }
        given.forEachIndexed { index, id -> repository.setPosition(id!!, index) }
    }

    private fun filterRow(
        userId: UUID,
        request: CreateSavedFilterRequest,
    ): SavedFilterRow {
        val name = request.name?.trim()
        val errors =
            listOfNotNull(
                (if (name == null) FieldErrorReason.REQUIRED else Validation.savedFilterName(name))
                    ?.let { FieldError("name", it) },
                if (request.definition == null) FieldError("definition", FieldErrorReason.REQUIRED) else null,
                request.categoryId?.let { FieldError("categoryId", FieldErrorReason.INVALID_FORMAT) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)
        filters.validate(request.definition!!)
        return SavedFilterRow(
            id = UUID.randomUUID(),
            userId = userId,
            kind = FILTER_KIND,
            name = name,
            categoryId = null,
            definition = filters.encode(request.definition!!),
            inFeed = request.inFeed,
            position = null,
        )
    }

    /** Закрепить можно корневую категорию набора. */
    private fun categoryRow(
        userId: UUID,
        request: CreateSavedFilterRequest,
    ): SavedFilterRow {
        if (request.name != null || request.definition != null) fail("kind", FieldErrorReason.INVALID_FORMAT)
        val id =
            request.categoryId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: fail(
                    "categoryId",
                    if (request.categoryId ==
                        null
                    ) {
                        FieldErrorReason.REQUIRED
                    } else {
                        FieldErrorReason.INVALID_FORMAT
                    },
                )
        if (!categories.isRoot(id) || categories.use(id) == CategoryUse.UNKNOWN) {
            fail("categoryId", FieldErrorReason.INVALID_FORMAT)
        }
        return SavedFilterRow(UUID.randomUUID(), userId, CATEGORY_KIND, null, id, null, inFeed = true, position = null)
    }

    private fun toDto(row: SavedFilterRow) =
        SavedFilter(
            id = row.id.toString(),
            kind = SavedFilterKind.valueOf(row.kind),
            inFeed = row.inFeed,
            name = row.name,
            categoryId = row.categoryId?.toString(),
            definition = row.definition?.let(filters::decode),
            position = row.position,
        )

    private fun fail(
        field: String,
        reason: FieldErrorReason,
    ): Nothing = throw validationFailed(listOf(FieldError(field, reason)))

    private fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}
