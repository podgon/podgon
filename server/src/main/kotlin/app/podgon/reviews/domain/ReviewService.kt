package app.podgon.reviews.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.PublicationInfo
import app.podgon.contract.PublicationRequest
import app.podgon.contract.REVIEW_EXCERPT_MAX
import app.podgon.contract.ReviewAuthor
import app.podgon.contract.ReviewCard
import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.SetPublicationsRequest
import app.podgon.contract.TagCount
import app.podgon.contract.UpdateReviewRequest
import app.podgon.contract.Validation
import app.podgon.groups.GroupAccess
import app.podgon.identity.UserDirectory
import app.podgon.reviews.ReviewsProperties
import app.podgon.reviews.persistence.CardRow
import app.podgon.reviews.persistence.Position
import app.podgon.reviews.persistence.PublicationRow
import app.podgon.reviews.persistence.ReviewRepository
import app.podgon.reviews.persistence.ReviewRow
import app.podgon.reviews.persistence.SearchFilters
import app.podgon.reviews.persistence.SearchPosition
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/** Параметры запроса списка: фильтры, курсор и размер страницы (в сыром виде — разбирает сервис). */
data class SearchParams(
    val q: String? = null,
    val categoryIds: List<String>? = null,
    val tags: List<String>? = null,
    val tagMode: String? = null,
    val authorId: String? = null,
    val cursor: String? = null,
    val limit: Int? = null,
)

private const val TAGS_LIMIT = 100

private fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/**
 * Рекомендации и их публикации в группы. Менять рекомендацию может только автор; читать — автор
 * и участники групп, где она опубликована (остальным — 404, существование не раскрываем).
 */
@Service
class ReviewService(
    private val reviews: ReviewRepository,
    private val access: GroupAccess,
    private val users: UserDirectory,
    private val props: ReviewsProperties,
    private val clock: Clock,
    private val tx: TransactionTemplate,
) {
    fun create(
        userId: UUID,
        request: CreateReviewRequest,
    ): ReviewDetails {
        val title = request.title.trim()
        val description = request.description.trim()
        val tags = normalizeTags(request.tags)
        validate(title, description, request.rating, tags)
        val publications = parsePublications(request.publications)
        val id = UUID.randomUUID()
        inTransaction {
            if (reviews.countByAuthor(userId) >=
                props.maxReviewsPerUser
            ) {
                throw ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
            }
            val now = now()
            reviews.insert(ReviewRow(id, userId, title, description, request.rating, now, now))
            reviews.replaceTags(id, tags)
            applyPublications(userId, id, publications, now)
        }
        return details(userId, id)
    }

    fun get(
        userId: UUID,
        reviewId: UUID,
    ): ReviewDetails = details(userId, reviewId)

    /** Частичное обновление: `null` — поле не менять. */
    fun update(
        userId: UUID,
        reviewId: UUID,
        request: UpdateReviewRequest,
    ): ReviewDetails {
        inTransaction {
            val current = requireAuthor(userId, reviewId)
            val title = request.title?.trim() ?: current.title
            val description = request.description?.trim() ?: current.description
            val rating = request.rating ?: current.rating
            val tags = request.tags?.let(::normalizeTags)
            validate(title, description, rating, tags.orEmpty())
            reviews.update(reviewId, title, description, rating, now())
            tags?.let { reviews.replaceTags(reviewId, it) }
        }
        return details(userId, reviewId)
    }

    fun setPublications(
        userId: UUID,
        reviewId: UUID,
        request: SetPublicationsRequest,
    ): ReviewDetails {
        val publications = parsePublications(request.publications)
        inTransaction {
            requireAuthor(userId, reviewId)
            applyPublications(userId, reviewId, publications, now())
        }
        return details(userId, reviewId)
    }

    fun delete(
        userId: UUID,
        reviewId: UUID,
    ) {
        inTransaction {
            requireAuthor(userId, reviewId)
            reviews.delete(reviewId)
        }
    }

    /** Лента одной группы с фильтрами (`GET /groups/{id}/reviews`). */
    fun feed(
        userId: UUID,
        groupId: UUID,
        params: SearchParams,
    ): ReviewPage = search(userId, listOf(groupId), params)

    /**
     * Поиск по публикациям. [groupIds] `null` — все группы пользователя. Чужая группа — 404. Результат —
     * публикации, поэтому рекомендация в двух группах приходит двумя карточками.
     */
    fun search(
        userId: UUID,
        groupIds: List<UUID>?,
        params: SearchParams,
    ): ReviewPage {
        val filters = parseFilters(params)
        val mine = access.memberGroupIds(userId)
        val groups = groupIds?.toSet()?.onEach { if (it !in mine) throw notFound() } ?: mine
        val size = pageSize(params.limit)
        val ranked = filters.query != null
        val after = decodeSearchCursor(params.cursor, ranked)
        val rows = if (groups.isEmpty()) emptyList() else reviews.search(groups, filters, after, size + 1)
        return page(rows, size) { encodeSearchCursor(it, ranked) }
    }

    /** Теги группы для автодополнения и панели фильтров. */
    fun groupTags(
        userId: UUID,
        groupId: UUID,
        prefix: String?,
    ): List<TagCount> {
        access.requireMember(userId, groupId)
        val normalized = prefix?.let(Validation::normalizeTag)?.takeIf { it.isNotEmpty() }
        if (normalized != null &&
            normalized.length > Validation.TAG_MAX
        ) {
            throw validationFailed(listOf(FieldError("prefix", FieldErrorReason.TOO_LONG)))
        }
        return reviews.groupTags(groupId, normalized, TAGS_LIMIT).map { TagCount(it.name, it.count) }
    }

    fun mine(
        userId: UUID,
        cursor: String?,
        limit: Int?,
    ): ReviewPage {
        val size = pageSize(limit)
        val rows = reviews.byAuthor(userId, decodeCursor(cursor), size + 1)
        return page(rows, size) { encodeCursor(it.review.createdAt, it.review.id) }
    }

    // --- внутреннее ---

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)

    /** Выполняет [block] в транзакции; нарушение внешнего ключа публикации — ошибка запроса, а не сбой сервера. */
    private fun inTransaction(block: () -> Unit) {
        try {
            tx.executeWithoutResult { block() }
        } catch (
            @Suppress("SwallowedException") e: DataIntegrityViolationException,
        ) {
            throw validationFailed(listOf(FieldError("publications", FieldErrorReason.INVALID_FORMAT)))
        }
    }

    private fun requireAuthor(
        userId: UUID,
        reviewId: UUID,
    ): ReviewRow {
        val row = reviews.find(reviewId) ?: throw notFound()
        if (row.authorId == userId) return row
        // не автору: видимая рекомендация — 403, невидимая — 404
        visibleTo(userId, reviewId, row)
        throw ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN)
    }

    /** Публикации, видимые [userId]; для постороннего (не автор и не участник групп публикаций) — 404. */
    private fun visibleTo(
        userId: UUID,
        reviewId: UUID,
        row: ReviewRow,
    ): List<PublicationRow> {
        val all = reviews.publications(reviewId)
        if (row.authorId == userId) return all
        val mine = access.memberGroupIds(userId)
        return all.filter { it.groupId in mine }.ifEmpty { throw notFound() }
    }

    private fun details(
        userId: UUID,
        reviewId: UUID,
    ): ReviewDetails {
        val row = reviews.find(reviewId) ?: throw notFound()
        val publications = visibleTo(userId, reviewId, row)
        return ReviewDetails(
            id = row.id.toString(),
            title = row.title,
            description = row.description,
            rating = row.rating,
            tags = reviews.tagsOf(listOf(reviewId))[reviewId].orEmpty(),
            author = author(row.authorId),
            createdAt = row.createdAt.toString(),
            updatedAt = row.updatedAt.toString(),
            publications =
                publications.map {
                    PublicationInfo(it.groupId.toString(), it.categoryId.toString(), it.sharedAt.toString())
                },
        )
    }

    private fun author(id: UUID): ReviewAuthor = authors(listOf(id)).getValue(id)

    private fun authors(ids: Collection<UUID>): Map<UUID, ReviewAuthor> =
        users.findAll(ids).mapValues { (_, u) -> ReviewAuthor(u.id.toString(), u.username, u.displayName) }

    private fun applyPublications(
        userId: UUID,
        reviewId: UUID,
        publications: List<Pair<UUID, UUID>>,
        now: Instant,
    ) {
        publications.forEach { (groupId, _) -> if (!access.isMember(userId, groupId)) throw notFound() }
        reviews.removePublicationsExcept(reviewId, publications.map { it.first })
        publications.forEach { (groupId, categoryId) -> reviews.upsertPublication(reviewId, groupId, categoryId, now) }
    }

    private fun parsePublications(requests: List<PublicationRequest>): List<Pair<UUID, UUID>> {
        val parsed =
            requests.map {
                (it.groupId.toUuidOrNull() ?: throw notFound()) to
                    (
                        it.categoryId.toUuidOrNull()
                            ?: throw validationFailed(
                                listOf(FieldError("publications", FieldErrorReason.INVALID_FORMAT)),
                            )
                    )
            }
        if (parsed.map { it.first }.distinct().size != parsed.size || parsed.size > props.maxGroupsPerReview) {
            throw validationFailed(listOf(FieldError("publications", FieldErrorReason.INVALID_FORMAT)))
        }
        return parsed
    }

    private fun normalizeTags(raw: List<String>): List<String> = raw.map(Validation::normalizeTag).distinct()

    private fun validate(
        title: String,
        description: String,
        rating: Int,
        tags: List<String>,
    ) {
        val errors =
            listOfNotNull(
                Validation.reviewTitle(title)?.let { FieldError("title", it) },
                Validation.reviewDescription(description)?.let { FieldError("description", it) },
                Validation.rating(rating)?.let { FieldError("rating", it) },
                Validation.tags(tags)?.let { FieldError("tags", it) },
            )
        if (errors.isNotEmpty()) throw validationFailed(errors)
    }

    private fun pageSize(limit: Int?): Int = (limit ?: props.defaultPageSize).coerceIn(1, props.maxPageSize)

    /** [rows] содержит на одну строку больше размера страницы: она нужна, чтобы понять, есть ли следующая. */
    private fun page(
        rows: List<CardRow>,
        size: Int,
        cursorOf: (CardRow) -> String,
    ): ReviewPage {
        val pageRows = rows.take(size)
        val ids = pageRows.map { it.review.id }
        val tags = reviews.tagsOf(ids)
        val authors = authors(pageRows.map { it.review.authorId }.distinct())
        val items =
            pageRows.map { r ->
                ReviewCard(
                    id = r.review.id.toString(),
                    title = r.review.title,
                    excerpt = r.review.description.take(REVIEW_EXCERPT_MAX),
                    rating = r.review.rating,
                    tags = tags[r.review.id].orEmpty(),
                    author = authors.getValue(r.review.authorId),
                    createdAt = r.review.createdAt.toString(),
                    categoryId = r.categoryId?.toString(),
                    sharedAt = r.sharedAt?.toString(),
                    groupId = r.groupId?.toString(),
                )
            }
        val next = if (rows.size > size) cursorOf(pageRows.last()) else null
        return ReviewPage(items, next)
    }

    private fun parseFilters(params: SearchParams): SearchFilters {
        val errors = mutableListOf<FieldError>()
        val query = params.q?.takeIf { it.isNotBlank() }
        params.q?.let { q -> Validation.searchQuery(q)?.let { errors += FieldError("q", it) } }
        val categories =
            params.categoryIds.orEmpty().mapNotNull { raw ->
                raw.toUuidOrNull().also {
                    if (it ==
                        null
                    ) {
                        errors += FieldError("categoryIds", FieldErrorReason.INVALID_FORMAT)
                    }
                }
            }
        if (categories.size >
            Validation.SEARCH_CATEGORIES_MAX
        ) {
            errors += FieldError("categoryIds", FieldErrorReason.TOO_LONG)
        }
        val tags =
            params.tags
                .orEmpty()
                .map(Validation::normalizeTag)
                .filter { it.isNotEmpty() }
                .distinct()
        Validation.tags(tags)?.let { errors += FieldError("tags", it) }
        val author =
            params.authorId?.let { raw ->
                raw.toUuidOrNull().also {
                    if (it ==
                        null
                    ) {
                        errors += FieldError("authorId", FieldErrorReason.INVALID_FORMAT)
                    }
                }
            }
        val anyTag =
            when (params.tagMode?.uppercase()) {
                null, "ALL" -> false
                "ANY" -> true
                else -> false.also { errors += FieldError("tagMode", FieldErrorReason.INVALID_FORMAT) }
            }
        if (errors.isNotEmpty()) throw validationFailed(errors)
        return SearchFilters(categories.toSet(), tags, anyTag, author, query?.trim())
    }

    private fun encodeSearchCursor(
        row: CardRow,
        ranked: Boolean,
    ): String {
        val mode = if (ranked) "r" else "d"
        val raw = "$mode|${row.score}|${row.sharedAt}|${row.review.id}|${row.groupId}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    /** Курсор другого режима (с запросом и без него) или испорченный — ошибка запроса. */
    private fun decodeSearchCursor(
        cursor: String?,
        ranked: Boolean,
    ): SearchPosition? {
        if (cursor.isNullOrEmpty()) return null
        return runCatching {
            val (mode, score, at, id, group) = String(Base64.getUrlDecoder().decode(cursor)).split('|')
            require(mode == if (ranked) "r" else "d")
            SearchPosition(score.toDouble(), Instant.parse(at), UUID.fromString(id), UUID.fromString(group))
        }.getOrElse { throw validationFailed(listOf(FieldError("cursor", FieldErrorReason.INVALID_FORMAT))) }
    }

    private fun encodeCursor(
        at: Instant,
        id: UUID,
    ): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id".toByteArray())

    private fun decodeCursor(cursor: String?): Position? {
        if (cursor.isNullOrEmpty()) return null
        return runCatching {
            val (at, id) = String(Base64.getUrlDecoder().decode(cursor)).split('|')
            Position(Instant.parse(at), UUID.fromString(id))
        }.getOrElse { throw validationFailed(listOf(FieldError("cursor", FieldErrorReason.INVALID_FORMAT))) }
    }
}
