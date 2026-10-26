package app.podgon.reviews.web

import app.podgon.common.ApiException
import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.SetPublicationsRequest
import app.podgon.contract.UpdateReviewRequest
import app.podgon.identity.AuthenticatedUser
import app.podgon.reviews.domain.ReviewService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Рекомендации и ленты. Права проверяются в [ReviewService], контроллер только разбирает запрос. */
@RestController
@RequestMapping("/api/v1")
class ReviewsController(
    private val reviews: ReviewService,
) {
    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: CreateReviewRequest,
    ): ReviewDetails = reviews.create(user.userId, body)

    @GetMapping("/reviews/{reviewId}")
    fun get(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
    ): ReviewDetails = reviews.get(user.userId, reviewId.uuid())

    @PatchMapping("/reviews/{reviewId}")
    fun update(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
        @RequestBody body: UpdateReviewRequest,
    ): ReviewDetails = reviews.update(user.userId, reviewId.uuid(), body)

    @PutMapping("/reviews/{reviewId}/groups")
    fun setPublications(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
        @RequestBody body: SetPublicationsRequest,
    ): ReviewDetails = reviews.setPublications(user.userId, reviewId.uuid(), body)

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
    ) = reviews.delete(user.userId, reviewId.uuid())

    @GetMapping("/me/reviews")
    fun mine(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ReviewPage = reviews.mine(user.userId, cursor, limit)

    @GetMapping("/groups/{groupId}/reviews")
    fun feed(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ReviewPage = reviews.feed(user.userId, groupId.uuid(), cursor, limit)

    /** Идентификатор из адреса; нераспознанный считается несуществующим ресурсом (404). */
    private fun String.uuid(): UUID =
        runCatching { UUID.fromString(this) }.getOrNull()
            ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}
