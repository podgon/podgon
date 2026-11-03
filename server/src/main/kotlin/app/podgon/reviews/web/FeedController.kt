package app.podgon.reviews.web

import app.podgon.common.ApiException
import app.podgon.contract.CreateSavedFilterRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.Facets
import app.podgon.contract.FeedToc
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterOrderRequest
import app.podgon.contract.UpdateSavedFilterRequest
import app.podgon.identity.AuthenticatedUser
import app.podgon.reviews.domain.FeedService
import app.podgon.reviews.domain.SavedFilterService
import app.podgon.reviews.domain.SearchParams
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

/** Оглавление ленты, счётчики панели фильтров и сохранённые фильтры (этап 8.2.2). */
@RestController
@RequestMapping("/api/v1")
class FeedController(
    private val feed: FeedService,
    private val savedFilters: SavedFilterService,
) {
    @GetMapping("/feed")
    fun toc(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) groupId: String?,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(required = false) tagMode: String?,
        @RequestParam(required = false) authorIds: List<String>?,
        @RequestParam(required = false) minRating: Int?,
        @RequestParam(required = false) maxRating: Int?,
    ): FeedToc =
        feed.toc(
            user.userId,
            groupId?.uuid(),
            SearchParams(null, categoryIds, tags, tagMode, authorIds, minRating, maxRating),
        )

    @GetMapping("/reviews/facets")
    fun facets(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) groupIds: List<String>?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(required = false) tagMode: String?,
        @RequestParam(required = false) authorIds: List<String>?,
        @RequestParam(required = false) minRating: Int?,
        @RequestParam(required = false) maxRating: Int?,
        @RequestParam(required = false) section: String?,
    ): Facets =
        feed.facets(
            user.userId,
            groupIds?.map { it.uuid() },
            SearchParams(q, categoryIds, tags, tagMode, authorIds, minRating, maxRating, section),
        )

    @GetMapping("/groups/{groupId}/facets")
    fun groupFacets(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(required = false) tagMode: String?,
        @RequestParam(required = false) authorIds: List<String>?,
        @RequestParam(required = false) minRating: Int?,
        @RequestParam(required = false) maxRating: Int?,
        @RequestParam(required = false) section: String?,
    ): Facets =
        feed.facets(
            user.userId,
            listOf(groupId.uuid()),
            SearchParams(q, categoryIds, tags, tagMode, authorIds, minRating, maxRating, section),
        )

    @GetMapping("/me/filters")
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): List<SavedFilter> = savedFilters.list(user.userId)

    @PostMapping("/me/filters")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: CreateSavedFilterRequest,
    ): SavedFilter = savedFilters.create(user.userId, body)

    @PatchMapping("/me/filters/{filterId}")
    fun update(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable filterId: String,
        @RequestBody body: UpdateSavedFilterRequest,
    ): SavedFilter = savedFilters.update(user.userId, filterId.uuid(), body)

    @DeleteMapping("/me/filters/{filterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable filterId: String,
    ) = savedFilters.delete(user.userId, filterId.uuid())

    @PutMapping("/me/filters/order")
    fun order(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody body: SavedFilterOrderRequest,
    ): List<SavedFilter> = savedFilters.order(user.userId, body)

    /** Идентификатор из адреса; нераспознанный считается несуществующим ресурсом (404). */
    private fun String.uuid(): UUID =
        runCatching { UUID.fromString(this) }.getOrNull()
            ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}
