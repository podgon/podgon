package app.podgon.reviews.domain

import app.podgon.common.ApiException
import app.podgon.common.validationFailed
import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.PublicationInfo
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
import app.podgon.identity.SystemGroupIds
import app.podgon.identity.UserDirectory
import app.podgon.media.Images
import app.podgon.media.MediaKeys
import app.podgon.media.MediaStorage
import app.podgon.reviews.ReviewsProperties
import app.podgon.reviews.persistence.CardRow
import app.podgon.reviews.persistence.Position
import app.podgon.reviews.persistence.PublicationRow
import app.podgon.reviews.persistence.ReviewRepository
import app.podgon.reviews.persistence.ReviewRow
import app.podgon.reviews.persistence.SearchPosition
import app.podgon.reviews.persistence.SharingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private const val TAGS_LIMIT = 100

private const val REGULAR = "REGULAR"
private const val PERSONAL = "PERSONAL"
private const val FAVORITES = "FAVORITES"

private fun notFound() = ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/**
 * Рекомендации и их публикации в группы. Менять рекомендацию может только автор; читать — автор
 * и участники групп, где она опубликована (остальным — 404, существование не раскрываем).
 * Рекомендация либо личная (опубликована только в личной группе автора), либо групповая (Л1).
 */
@Service
class ReviewService(
    private val reviews: ReviewRepository,
    private val sharing: SharingRepository,
    private val categories: CategoryService,
    private val access: GroupAccess,
    private val filters: FeedFilters,
    private val users: UserDirectory,
    private val props: ReviewsProperties,
    private val clock: Clock,
    private val tx: TransactionTemplate,
    private val storage: MediaStorage,
    private val images: Images,
    private val subscriptions: SubscriptionMatcher,
) {
    fun create(
        userId: UUID,
        request: CreateReviewRequest,
    ): ReviewDetails {
        val title = request.title.trim()
        val description = request.description.trim()
        val tags = normalizeTags(request.tags)
        val categoryId = parseCategory(request.categoryId)
        val groupIds = parseGroups(request.groupIds)
        validate(title, description, request.rating, tags)
        requireCategoryAllowed(categoryId, personal = groupIds.isEmpty())
        val id = UUID.randomUUID()
        val system = system(userId)
        inTransaction {
            if (reviews.countByAuthor(userId) >=
                props.maxReviewsPerUser
            ) {
                throw ApiException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.CONFLICT)
            }
            val now = now()
            reviews.insert(ReviewRow(id, userId, title, description, request.rating, categoryId, now, now))
            reviews.replaceTags(id, tags)
            applyVisibility(userId, system, id, groupIds, now)
        }
        subscriptions.published(id, userId, title, groupIds)
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
            val categoryId = request.categoryId?.let(::parseCategory) ?: current.categoryId
            val tags = request.tags?.let(::normalizeTags)
            validate(title, description, rating, tags.orEmpty())
            if (categoryId != current.categoryId) {
                requireCategoryAllowed(categoryId, personal = isPersonal(userId, reviewId))
            }
            reviews.update(reviewId, title, description, rating, categoryId, now())
            tags?.let { reviews.replaceTags(reviewId, it) }
        }
        return details(userId, reviewId)
    }

    fun setPublications(
        userId: UUID,
        reviewId: UUID,
        request: SetPublicationsRequest,
    ): ReviewDetails {
        val groupIds = parseGroups(request.groupIds)
        val system = system(userId)
        var added = emptyList<UUID>()
        var title = ""
        inTransaction {
            val current = requireAuthor(userId, reviewId)
            reviews.lock(reviewId)
            if (groupIds.isNotEmpty()) requireCategoryAllowed(current.categoryId, personal = false)
            val before =
                reviews
                    .publications(reviewId)
                    .filter { it.kind == REGULAR }
                    .map { it.groupId }
                    .toSet()
            applyVisibility(userId, system, reviewId, groupIds, now())
            // снятые публикации: рекомендация могла пропасть из чужого «Избранного» (Ф1)
            sharing.cleanupFavorites(reviewId = reviewId)
            added = groupIds - before
            title = current.title
        }
        // подписки — только на новые для рекомендации группы
        subscriptions.published(reviewId, userId, title, added)
        return details(userId, reviewId)
    }

    fun delete(
        userId: UUID,
        reviewId: UUID,
    ) {
        var photoId: UUID? = null
        inTransaction {
            photoId = requireAuthor(userId, reviewId).photoId
            reviews.delete(reviewId)
        }
        photoId?.let { storage.delete(MediaKeys.photo(it.toString())) }
    }

    /** Лента одной группы с фильтрами (`GET /groups/{id}/reviews`). */
    fun feed(
        userId: UUID,
        groupId: UUID,
        params: SearchParams,
    ): ReviewPage = search(userId, listOf(groupId), params)

    /**
     * Лента и поиск: каждая рекомендация один раз (И15). [groupIds] `null` — все обычные группы пользователя
     * (без «Личного» и «Избранного», Л2); явно можно указать и свои системные группы. Чужая группа — 404.
     * Без запроса — по самой свежей публикации в области, с запросом — по релевантности; `section` сужает выборку.
     */
    fun search(
        userId: UUID,
        groupIds: List<UUID>?,
        params: SearchParams,
    ): ReviewPage {
        val parsed = filters.parse(userId, params)
        val groups = filters.scope(userId, groupIds)
        val size = pageSize(params.limit)
        val ranked = parsed.query != null
        val after = decodeSearchCursor(params.cursor, ranked)
        val rows = if (groups.isEmpty()) emptyList() else reviews.search(groups, parsed, after, size + 1)
        return page(userId, rows, size) { encodeSearchCursor(it, ranked) }
    }

    /** Теги группы (в том числе своей системной) для автодополнения и панели фильтров. */
    fun groupTags(
        userId: UUID,
        groupId: UUID,
        prefix: String?,
    ): List<TagCount> = tags(filters.scope(userId, listOf(groupId)), prefix)

    /** Теги моих обычных групп и «Личного» — автодополнение в форме и на «Все». */
    fun myTags(
        userId: UUID,
        prefix: String?,
    ): List<TagCount> = tags(filters.scope(userId, null) + filters.system(userId).personal, prefix)

    private fun tags(
        groups: Set<UUID>,
        prefix: String?,
    ): List<TagCount> {
        val normalized = prefix?.let(Validation::normalizeTag)?.takeIf { it.isNotEmpty() }
        if (normalized != null &&
            normalized.length > Validation.TAG_MAX
        ) {
            throw validationFailed(listOf(FieldError("prefix", FieldErrorReason.TOO_LONG)))
        }
        return reviews.tags(groups, normalized, TAGS_LIMIT).map { TagCount(it.name, it.count) }
    }

    /**
     * Фото рекомендации (автор): уменьшенный JPEG без метаданных. Файл пишется до ссылки в БД, прежний удаляется
     * после; файлы без ссылок (удалённые рекомендации) убирает ночная чистка [PhotoCleanup].
     */
    fun setPhoto(
        userId: UUID,
        reviewId: UUID,
        raw: ByteArray,
    ): ReviewDetails {
        val current = requireAuthor(userId, reviewId)
        val photo = images.photo(raw)
        val id = UUID.randomUUID()
        storage.put(MediaKeys.photo(id.toString()), photo)
        reviews.setPhoto(reviewId, id, now())
        current.photoId?.let { storage.delete(MediaKeys.photo(it.toString())) }
        return details(userId, reviewId)
    }

    fun removePhoto(
        userId: UUID,
        reviewId: UUID,
    ): ReviewDetails {
        val current = requireAuthor(userId, reviewId)
        reviews.setPhoto(reviewId, null, now())
        current.photoId?.let { storage.delete(MediaKeys.photo(it.toString())) }
        return details(userId, reviewId)
    }

    /** Фото видимой мне рекомендации; невидимая или без фото — 404. */
    fun photo(
        userId: UUID,
        reviewId: UUID,
    ): ByteArray {
        val row = reviews.find(reviewId) ?: throw notFound()
        visibleTo(userId, reviewId, row)
        val photoId = row.photoId ?: throw notFound()
        return storage.get(MediaKeys.photo(photoId.toString())) ?: throw notFound()
    }

    /**
     * Добавляет видимую мне рекомендацию в моё «Избранное» (в том числе свою); невидимая — 404. Дата добавления —
     * время публикации в «Избранном» (по ней лента «Избранного» идёт новыми сверху).
     */
    fun addFavorite(
        userId: UUID,
        reviewId: UUID,
    ) {
        val favorites = system(userId).favorites
        inTransaction {
            val row = reviews.find(reviewId) ?: throw notFound()
            visibleTo(userId, reviewId, row)
            reviews.addPublication(reviewId, favorites, now())
        }
    }

    /** Убирает рекомендацию из моего «Избранного»; если её там нет — ничего не меняет. */
    fun removeFavorite(
        userId: UUID,
        reviewId: UUID,
    ) = reviews.removePublication(reviewId, system(userId).favorites)

    /** «Мои рекомендации»: групповые, где я автор (И21); личные — в ленте личной группы. */
    fun mine(
        userId: UUID,
        cursor: String?,
        limit: Int?,
    ): ReviewPage {
        val size = pageSize(limit)
        val rows = reviews.byAuthor(userId, decodeCursor(cursor), size + 1)
        return page(userId, rows, size) { encodeCursor(it.review.createdAt, it.review.id) }
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
            throw validationFailed(listOf(FieldError("groupIds", FieldErrorReason.INVALID_FORMAT)))
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

    /**
     * Публикации, видимые [userId]: автору — все, остальным — в их обычных группах; постороннему (не автор
     * и не участник групп публикаций) — 404. Публикации в «Избранном» не показываются никому.
     */
    private fun visibleTo(
        userId: UUID,
        reviewId: UUID,
        row: ReviewRow,
    ): List<PublicationRow> {
        val all = reviews.publications(reviewId).filter { it.kind != FAVORITES }
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
        val regular = publications.filter { it.kind == REGULAR }
        val roles = access.rolesOf(row.authorId, regular.map { it.groupId })
        return ReviewDetails(
            id = row.id.toString(),
            title = row.title,
            description = row.description,
            rating = row.rating,
            categoryId = row.categoryId.toString(),
            tags = reviews.tagsOf(listOf(reviewId))[reviewId].orEmpty(),
            author = author(row.authorId),
            createdAt = row.createdAt.toString(),
            updatedAt = row.updatedAt.toString(),
            personal = publications.any { it.kind == PERSONAL },
            publications =
                regular.map {
                    PublicationInfo(
                        it.groupId.toString(),
                        it.sharedAt.toString(),
                        roles[it.groupId],
                    )
                },
            favorite = reviews.publishedIn(system(userId).favorites, listOf(reviewId)).isNotEmpty(),
            photoId = row.photoId?.toString(),
        )
    }

    private fun author(id: UUID): ReviewAuthor = authors(listOf(id)).getValue(id)

    private fun authors(ids: Collection<UUID>): Map<UUID, ReviewAuthor> =
        users.findAll(ids).mapValues { (_, u) ->
            ReviewAuthor(u.id.toString(), u.username, u.displayName, u.avatarId?.toString())
        }

    private fun system(userId: UUID): SystemGroupIds = filters.system(userId)

    private fun isPersonal(
        userId: UUID,
        reviewId: UUID,
    ): Boolean = reviews.publications(reviewId).any { it.kind == PERSONAL && it.groupId == system(userId).personal }

    /** Группы публикации: только обычные, где автор участник; свои системные — ошибка запроса, чужие — 404. */
    private fun applyVisibility(
        userId: UUID,
        system: SystemGroupIds,
        reviewId: UUID,
        groupIds: List<UUID>,
        now: Instant,
    ) {
        groupIds.forEach { groupId ->
            if (groupId == system.personal || groupId == system.favorites) {
                throw validationFailed(listOf(FieldError("groupIds", FieldErrorReason.INVALID_FORMAT)))
            }
            if (!access.isMember(userId, groupId)) throw notFound()
        }
        reviews.setVisibility(reviewId, groupIds, system.personal, now)
    }

    private fun parseGroups(raw: List<String>): List<UUID> {
        val parsed = raw.map { it.toUuidOrNull() ?: throw notFound() }
        if (parsed.distinct().size != parsed.size || parsed.size > props.maxGroupsPerReview) {
            throw validationFailed(listOf(FieldError("groupIds", FieldErrorReason.INVALID_FORMAT)))
        }
        return parsed
    }

    private fun parseCategory(raw: String): UUID =
        raw.toUuidOrNull()
            ?: throw validationFailed(listOf(FieldError("categoryId", FieldErrorReason.INVALID_FORMAT)))

    /** Категория из набора и не скрытая; «Без категории» — только у личной (И28, причина `REQUIRED`). */
    private fun requireCategoryAllowed(
        categoryId: UUID,
        personal: Boolean,
    ) {
        when (categories.use(categoryId)) {
            CategoryUse.OK -> Unit
            CategoryUse.PERSONAL_ONLY ->
                if (!personal) throw validationFailed(listOf(FieldError("categoryId", FieldErrorReason.REQUIRED)))
            CategoryUse.UNKNOWN -> throw validationFailed(
                listOf(FieldError("categoryId", FieldErrorReason.INVALID_FORMAT)),
            )
        }
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
        userId: UUID,
        rows: List<CardRow>,
        size: Int,
        cursorOf: (CardRow) -> String,
    ): ReviewPage {
        val pageRows = rows.take(size)
        val ids = pageRows.map { it.review.id }
        val tags = reviews.tagsOf(ids)
        val favorites = reviews.publishedIn(system(userId).favorites, ids)
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
                    categoryId = r.review.categoryId.toString(),
                    sharedAt = r.sharedAt.toString(),
                    groupIds = r.groupIds.map(UUID::toString),
                    favorite = r.review.id in favorites,
                    photoId = r.review.photoId?.toString(),
                )
            }
        val next = if (rows.size > size) cursorOf(pageRows.last()) else null
        return ReviewPage(items, next)
    }

    private fun encodeSearchCursor(
        row: CardRow,
        ranked: Boolean,
    ): String {
        val mode = if (ranked) "r" else "d"
        val raw = "$mode|${row.score}|${row.sharedAt}|${row.review.id}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    /** Курсор другого режима (с запросом и без него) или испорченный — ошибка запроса. */
    private fun decodeSearchCursor(
        cursor: String?,
        ranked: Boolean,
    ): SearchPosition? {
        if (cursor.isNullOrEmpty()) return null
        return runCatching {
            val (mode, score, at, id) =
                String(Base64.getUrlDecoder().decode(cursor)).split('|').also {
                    require(
                        it.size == 4,
                    )
                }
            require(mode == if (ranked) "r" else "d")
            SearchPosition(score.toDouble(), Instant.parse(at), UUID.fromString(id))
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
