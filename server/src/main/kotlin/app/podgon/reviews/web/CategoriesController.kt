package app.podgon.reviews.web

import app.podgon.common.ApiException
import app.podgon.contract.CategoryDeleteImpact
import app.podgon.contract.CategoryInfo
import app.podgon.contract.CreateCategoryRequest
import app.podgon.contract.ErrorCode
import app.podgon.contract.MoveCategoryRequest
import app.podgon.contract.UpdateCategoryRequest
import app.podgon.identity.AuthenticatedUser
import app.podgon.reviews.domain.CategoryService
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

/** Категории группы. Права проверяются в [CategoryService], контроллер только разбирает запрос. */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/categories")
class CategoriesController(
    private val categories: CategoryService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
    ): List<CategoryInfo> = categories.list(user.userId, groupId.uuid())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @RequestBody body: CreateCategoryRequest,
    ): CategoryInfo = categories.create(user.userId, groupId.uuid(), body)

    @PatchMapping("/{categoryId}")
    fun update(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable categoryId: String,
        @RequestBody body: UpdateCategoryRequest,
    ): CategoryInfo = categories.update(user.userId, groupId.uuid(), categoryId.uuid(), body)

    @PutMapping("/{categoryId}/parent")
    fun move(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable categoryId: String,
        @RequestBody body: MoveCategoryRequest,
    ): CategoryInfo = categories.move(user.userId, groupId.uuid(), categoryId.uuid(), body)

    @GetMapping("/{categoryId}/delete-impact")
    fun deleteImpact(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable categoryId: String,
    ): CategoryDeleteImpact = categories.deleteImpact(user.userId, groupId.uuid(), categoryId.uuid())

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable groupId: String,
        @PathVariable categoryId: String,
        @RequestParam(defaultValue = "false") confirm: Boolean,
    ) = categories.delete(user.userId, groupId.uuid(), categoryId.uuid(), confirm)

    /** Идентификатор из адреса; нераспознанный считается несуществующим ресурсом (404). */
    private fun String.uuid(): UUID =
        runCatching { UUID.fromString(this) }.getOrNull()
            ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
}
