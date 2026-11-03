package app.podgon.reviews.web

import app.podgon.common.ApiException
import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.SetPublicationsRequest
import app.podgon.contract.TagCount
import app.podgon.contract.UpdateReviewRequest
import app.podgon.identity.AuthenticatedUser
import app.podgon.media.Images
import app.podgon.media.imageResponse
import app.podgon.reviews.domain.ReviewService
import app.podgon.reviews.domain.SearchParams
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    private val images: Images,
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

    @PutMapping("/reviews/{reviewId}/photo", consumes = ["image/jpeg", "image/png"])
    fun setPhoto(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
        request: HttpServletRequest,
    ): ReviewDetails = reviews.setPhoto(user.userId, reviewId.uuid(), images.read(request))

    @DeleteMapping("/reviews/{reviewId}/photo")
    fun removePhoto(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
    ): ReviewDetails = reviews.removePhoto(user.userId, reviewId.uuid())

    @GetMapping("/reviews/{reviewId}/photo", produces = ["image/jpeg"])
    fun photo(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
    ): ResponseEntity<ByteArray> = imageResponse(reviews.photo(user.userId, reviewId.uuid()))

    @PutMapping("/me/favorites/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addFavorite(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
    ) = reviews.addFavorite(user.userId, reviewId.uuid())

    @DeleteMapping("/me/favorites/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFavorite(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable reviewId: String,
    ) = reviews.removeFavorite(user.userId, reviewId.uuid())

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
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) titleOnly: Boolean?,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(required = false) tagMode: String?,
        @RequestParam(required = false) authorIds: List<String>?,
        @RequestParam(required = false) minRating: Int?,
        @RequestParam(required = false) maxRating: Int?,
        @RequestParam(required = false) section: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ReviewPage =
        reviews.feed(
            user.userId,
            groupId.uuid(),
            SearchParams(
                q,
                categoryIds,
                tags,
                tagMode,
                authorIds,
                minRating,
                maxRating,
                section,
                cursor,
                limit,
                titleOnly,
            ),
        )

    @GetMapping("/reviews")
    fun search(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) groupIds: List<String>?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) titleOnly: Boolean?,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(required = false) tagMode: String?,
        @RequestParam(required = false) authorIds: List<String>?,
        @RequestParam(required = false) minRating: Int?,
        @RequestParam(required = false) maxRating: Int?,
        @RequestParam(required = false) section: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ReviewPage =
        reviews.search(
            user.userId,
            groupIds?.map { it.uuid() },
            SearchParams(
                q,
                categoryIds,
                tags,
                tagMode,
                authorIds,
                minRating,
                maxRating,
                section,
                cursor,
                limit,
                titleOnly,
            ),
        )

    @GetMapping("/me/tags")
    fun myTags(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) prefix: String?,
    ): List<TagCount> = reviews.myTags(user.userId, prefix)

    @GetMapping("/groups/{groupId}/tags")
    fun tags(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestParam(required = false) prefix: String?,
    ): List<TagCount> = reviews.groupTags(user.userId, groupId.uuid(), prefix)

    /** Идентификатор из адреса; нераспознанный считается несуществующим ресурсом (404). */
    private fun String.uuid(): UUID =
        runCatching { UUID.fromString(this) }.getOrNull()
            ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}
