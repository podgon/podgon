package app.podgon.mobile.core.network

import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.SetPublicationsRequest
import app.podgon.contract.UpdateReviewRequest
import io.ktor.client.HttpClient
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

    suspend fun feed(
        groupId: String,
        cursor: String? = null,
    ): Result<ReviewPage> =
        apiCall {
            client
                .get(
                    "api/v1/groups/$groupId/reviews",
                ) { cursor?.let { parameter("cursor", it) } }
                .bodyOrThrow()
        }
}
