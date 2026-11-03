package app.podgon.reviews.web

import app.podgon.common.validationFailed
import app.podgon.contract.CategorySet
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import app.podgon.identity.AuthenticatedUser
import app.podgon.identity.UserDirectory
import app.podgon.reviews.domain.CategoryService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Общий набор категорий; без `locale` — на языке профиля. Клиент кэширует набор по `ETag`. */
@RestController
@RequestMapping("/api/v1/categories")
class CategoriesController(
    private val categories: CategoryService,
    private val users: UserDirectory,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false) locale: String?,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
    ): ResponseEntity<CategorySet> {
        val lang = locale ?: users.find(user.userId)?.locale ?: "ru"
        if (lang !in LOCALES) throw validationFailed(listOf(FieldError("locale", FieldErrorReason.INVALID_FORMAT)))
        val set = categories.set(lang)
        val etag = "\"${set.version}\""
        val matches =
            ifNoneMatch
                ?.split(',')
                ?.map { it.trim().removePrefix("W/") }
                ?.any { it == etag || it == "*" } == true
        return if (matches) {
            ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build()
        } else {
            ResponseEntity.ok().eTag(etag).body(set)
        }
    }

    private companion object {
        val LOCALES = setOf("ru", "en")
    }
}
