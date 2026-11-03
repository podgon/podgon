package app.podgon.reviews.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.FilterDefinition
import app.podgon.contract.SECTION_CATEGORY_PREFIX
import app.podgon.contract.SECTION_FILTER_PREFIX
import app.podgon.contract.TagMode
import app.podgon.contract.Validation
import app.podgon.groups.GroupAccess
import app.podgon.identity.SystemGroupIds
import app.podgon.identity.SystemGroups
import app.podgon.reviews.persistence.Criteria
import app.podgon.reviews.persistence.FeedRepository
import app.podgon.reviews.persistence.SavedFilterRepository
import app.podgon.reviews.persistence.SearchFilters
import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/** Параметры запроса списка: фильтры панели, секция, курсор и размер страницы (в сыром виде — разбирает сервис). */
data class SearchParams(
    val q: String? = null,
    val categoryIds: List<String>? = null,
    val tags: List<String>? = null,
    val tagMode: String? = null,
    val authorIds: List<String>? = null,
    val minRating: Int? = null,
    val maxRating: Int? = null,
    val section: String? = null,
    val cursor: String? = null,
    val limit: Int? = null,
)

/** Хранимый вид определения фильтра: значения по умолчанию тоже пишутся, неизвестные поля не мешают чтению. */
internal val definitionJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/**
 * Область и фильтры лент, фасетов и оглавления. Область — обычные группы пользователя (без системных, Л2)
 * либо явно указанные, в том числе свои системные; чужая группа — 404. Фильтр панели и секция складываются
 * через «и» (пересечение).
 */
@Service
class FeedFilters(
    private val access: GroupAccess,
    private val systemGroups: SystemGroups,
    private val categories: CategoryService,
    private val savedFilters: SavedFilterRepository,
    private val feed: FeedRepository,
) {
    fun system(userId: UUID): SystemGroupIds =
        systemGroups.of(userId) ?: throw ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)

    /** Группы области: [groupIds] `null` — все обычные группы пользователя. */
    fun scope(
        userId: UUID,
        groupIds: Collection<UUID>?,
    ): Set<UUID> {
        val mine = access.memberGroupIds(userId)
        if (groupIds == null) return mine
        val readable = mine + system(userId).let { setOf(it.personal, it.favorites) }
        groupIds.forEach { if (it !in readable) throw notFound() }
        return groupIds.toSet()
    }

    /** Разбирает фильтр панели и секцию; секция и поиск вместе — ошибка (в секциях поиска нет). */
    fun parse(
        userId: UUID,
        params: SearchParams,
    ): SearchFilters {
        val errors = mutableListOf<FieldError>()
        val query = params.q?.takeIf { it.isNotBlank() }?.trim()
        params.q?.let { q -> Validation.searchQuery(q)?.let { errors += FieldError("q", it) } }
        val panel =
            criteria(
                categoryIds = params.categoryIds.orEmpty(),
                tags = params.tags.orEmpty(),
                tagMode = params.tagMode,
                authorIds = params.authorIds.orEmpty(),
                minRating = params.minRating,
                maxRating = params.maxRating,
                errors = errors,
                prefix = "",
            )
        if (params.section != null && query != null) {
            errors += FieldError("section", FieldErrorReason.INVALID_FORMAT)
        }
        if (errors.isNotEmpty()) throw validationFailed(errors)
        val section = params.section?.let { section(userId, it) }
        return SearchFilters(listOfNotNull(panel.takeIf { it != Criteria() }, section), query)
    }

    /** Условия сохранённого фильтра в секции ленты: поиска нет, удалённые теги игнорируются. */
    fun sectionCriteria(definition: FilterDefinition): Criteria {
        val c = criteria(definition, mutableListOf(), "definition.")
        return c.copy(tags = feed.existingTags(c.tags).let { known -> c.tags.filter { it in known } })
    }

    /** Проверяет определение сохранённого фильтра; ошибки — с префиксом `definition.`. */
    fun validate(definition: FilterDefinition) {
        val errors = mutableListOf<FieldError>()
        if (definition.v != 1) errors += FieldError("definition.v", FieldErrorReason.INVALID_FORMAT)
        definition.q?.let { q -> Validation.searchQuery(q)?.let { errors += FieldError("definition.q", it) } }
        criteria(definition, errors, "definition.")
        if (errors.isNotEmpty()) throw validationFailed(errors)
    }

    fun decode(json: String): FilterDefinition = definitionJson.decodeFromString(FilterDefinition.serializer(), json)

    fun encode(definition: FilterDefinition): String =
        definitionJson.encodeToString(FilterDefinition.serializer(), definition)

    private fun criteria(
        definition: FilterDefinition,
        errors: MutableList<FieldError>,
        prefix: String,
    ) = criteria(
        categoryIds = definition.categoryIds,
        tags = definition.tags,
        tagMode = definition.tagMode.name,
        authorIds = definition.authorIds,
        minRating = definition.minRating,
        maxRating = definition.maxRating,
        errors = errors,
        prefix = prefix,
    )

    @Suppress("LongParameterList")
    private fun criteria(
        categoryIds: List<String>,
        tags: List<String>,
        tagMode: String?,
        authorIds: List<String>,
        minRating: Int?,
        maxRating: Int?,
        errors: MutableList<FieldError>,
        prefix: String,
    ): Criteria {
        val categoryUuids = uuids(categoryIds, "${prefix}categoryIds", Validation.SEARCH_CATEGORIES_MAX, errors)
        val authorUuids = uuids(authorIds, "${prefix}authorIds", Validation.SEARCH_AUTHORS_MAX, errors)
        val normalized = tags.map(Validation::normalizeTag).filter { it.isNotEmpty() }.distinct()
        Validation.tags(normalized)?.let { errors += FieldError("${prefix}tags", it) }
        val anyTag =
            when (tagMode?.uppercase()) {
                null, TagMode.ALL.name -> false
                TagMode.ANY.name -> true
                else -> false.also { errors += FieldError("${prefix}tagMode", FieldErrorReason.INVALID_FORMAT) }
            }
        minRating?.let { r -> Validation.rating(r)?.let { errors += FieldError("${prefix}minRating", it) } }
        maxRating?.let { r -> Validation.rating(r)?.let { errors += FieldError("${prefix}maxRating", it) } }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            errors += FieldError("${prefix}minRating", FieldErrorReason.INVALID_FORMAT)
        }
        return Criteria(categoryUuids, normalized, anyTag, authorUuids, minRating, maxRating)
    }

    private fun uuids(
        raw: List<String>,
        field: String,
        max: Int,
        errors: MutableList<FieldError>,
    ): Set<UUID> {
        val parsed = raw.map { it.toUuidOrNull() }
        if (parsed.any { it == null }) errors += FieldError(field, FieldErrorReason.INVALID_FORMAT)
        val result = parsed.filterNotNull().toSet()
        if (result.size > max) errors += FieldError(field, FieldErrorReason.TOO_LONG)
        return result
    }

    /** `category:<id>` — категория набора; `filter:<id>` — свой сохранённый фильтр (чужой или удалённый — 404). */
    private fun section(
        userId: UUID,
        raw: String,
    ): Criteria {
        val invalid = { validationFailed(listOf(FieldError("section", FieldErrorReason.INVALID_FORMAT))) }
        return when {
            raw.startsWith(SECTION_CATEGORY_PREFIX) -> {
                val id = raw.removePrefix(SECTION_CATEGORY_PREFIX).toUuidOrNull() ?: throw invalid()
                if (!categories.exists(id)) throw invalid()
                Criteria(categoryIds = setOf(id))
            }
            raw.startsWith(SECTION_FILTER_PREFIX) -> {
                val id = raw.removePrefix(SECTION_FILTER_PREFIX).toUuidOrNull() ?: throw invalid()
                val row = savedFilters.find(userId, id)?.takeIf { it.kind == FILTER_KIND } ?: throw notFound()
                sectionCriteria(decode(row.definition!!))
            }
            else -> throw invalid()
        }
    }

    private fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}

internal const val FILTER_KIND = "FILTER"
internal const val CATEGORY_KIND = "CATEGORY"
