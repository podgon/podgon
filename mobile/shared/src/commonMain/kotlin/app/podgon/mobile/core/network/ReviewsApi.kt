package app.podgon.mobile.core.network

import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.Facets
import app.podgon.contract.FeedToc
import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.SetPublicationsRequest
import app.podgon.contract.TagCount
import app.podgon.contract.UpdateReviewRequest
import app.podgon.mobile.feature.reviews.ReviewFilter
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Рекомендации, публикации и ленты (`api/openapi.yaml`, тег reviews; клиент с авторизацией). */
class ReviewsApi(
    private val client: HttpClient,
) {
    suspend fun create(request: CreateReviewRequest): Result<ReviewDetails> =
        apiCall { client.post("api/v1/reviews") { setBody(request) }.bodyOrThrow() }

    suspend fun get(reviewId: String): Result<ReviewDetails> =
        apiCall {
            client.get("api/v1/reviews/$reviewId").bodyOrThrow()
        }

    suspend fun update(
        reviewId: String,
        request: UpdateReviewRequest,
    ): Result<ReviewDetails> = apiCall { client.patch("api/v1/reviews/$reviewId") { setBody(request) }.bodyOrThrow() }

    suspend fun setPublications(
        reviewId: String,
        request: SetPublicationsRequest,
    ): Result<ReviewDetails> =
        apiCall { client.put("api/v1/reviews/$reviewId/groups") { setBody(request) }.bodyOrThrow() }

    suspend fun delete(reviewId: String): Result<Unit> =
        apiCall {
            client.delete("api/v1/reviews/$reviewId").bodyOrThrow<Unit>()
        }

    suspend fun mine(cursor: String? = null): Result<ReviewPage> =
        apiCall { client.get("api/v1/me/reviews") { cursor?.let { parameter("cursor", it) } }.bodyOrThrow() }

    /** Лента и поиск в одной группе; [section] — записи одной секции оглавления. */
    suspend fun feed(
        groupId: String,
        filter: ReviewFilter = ReviewFilter(),
        cursor: String? = null,
        section: String? = null,
    ): Result<ReviewPage> =
        apiCall {
            client.get("api/v1/groups/$groupId/reviews") { applyFilter(filter, cursor, section) }.bodyOrThrow()
        }

    /** «Все» и поиск по всем своим группам; каждая рекомендация один раз. */
    suspend fun search(
        filter: ReviewFilter,
        cursor: String? = null,
        section: String? = null,
    ): Result<ReviewPage> =
        apiCall { client.get("api/v1/reviews") { applyFilter(filter, cursor, section) }.bodyOrThrow() }

    /** Оглавление ленты: [groupId] `null` — «Все». Поиск в оглавлении не участвует. */
    suspend fun toc(
        groupId: String?,
        filter: ReviewFilter = ReviewFilter(),
    ): Result<FeedToc> =
        apiCall {
            client
                .get("api/v1/feed") {
                    groupId?.let { parameter("groupId", it) }
                    applyFilter(filter.copy(query = ""), null, null)
                }.bodyOrThrow()
        }

    /** Счётчики для панели фильтров: [groupId] `null` — «Все». */
    suspend fun facets(
        groupId: String?,
        filter: ReviewFilter = ReviewFilter(),
        section: String? = null,
    ): Result<Facets> =
        apiCall {
            val path = if (groupId == null) "api/v1/reviews/facets" else "api/v1/groups/$groupId/facets"
            client.get(path) { applyFilter(filter, null, section) }.bodyOrThrow()
        }

    /** Теги моих групп и «Личного» (автодополнение в форме и на «Все»). */
    suspend fun myTags(prefix: String? = null): Result<List<TagCount>> =
        apiCall { client.get("api/v1/me/tags") { prefix?.let { parameter("prefix", it) } }.bodyOrThrow() }

    /** Теги группы с числом публикаций (подсказки и панель фильтров). */
    suspend fun tags(
        groupId: String,
        prefix: String? = null,
    ): Result<List<TagCount>> =
        apiCall { client.get("api/v1/groups/$groupId/tags") { prefix?.let { parameter("prefix", it) } }.bodyOrThrow() }

    private fun HttpRequestBuilder.applyFilter(
        filter: ReviewFilter,
        cursor: String?,
        section: String?,
    ) {
        filter.query
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let {
                parameter("q", it)
                if (filter.titleOnly) parameter("titleOnly", true)
            }
        if (filter.categoryIds.isNotEmpty()) parameter("categoryIds", filter.categoryIds.joinToString(","))
        if (filter.tags.isNotEmpty()) {
            parameter("tags", filter.tags.joinToString(","))
            if (filter.anyTag) parameter("tagMode", "ANY")
        }
        if (filter.authorIds.isNotEmpty()) parameter("authorIds", filter.authorIds.joinToString(","))
        filter.minRating?.let { parameter("minRating", it) }
        filter.maxRating?.let { parameter("maxRating", it) }
        section?.let { parameter("section", it) }
        cursor?.let { parameter("cursor", it) }
    }
}
