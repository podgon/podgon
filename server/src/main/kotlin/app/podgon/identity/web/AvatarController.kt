package app.podgon.identity.web

import app.podgon.common.ApiException
import app.podgon.contract.ErrorCode
import app.podgon.contract.UserProfile
import app.podgon.identity.AuthenticatedUser
import app.podgon.identity.domain.AvatarService
import app.podgon.media.Images
import app.podgon.media.imageResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Аватары: свой — загрузить и убрать, любой — посмотреть (всем вошедшим). Тело загрузки — сама картинка. */
@RestController
@RequestMapping("/api/v1")
class AvatarController(
    private val avatars: AvatarService,
    private val images: Images,
) {
    @PutMapping("/me/avatar", consumes = ["image/jpeg", "image/png"])
    fun upload(
        @AuthenticationPrincipal user: AuthenticatedUser,
        request: HttpServletRequest,
    ): UserProfile = avatars.set(user.userId, images.read(request))

    @DeleteMapping("/me/avatar")
    fun remove(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): UserProfile = avatars.remove(user.userId)

    @GetMapping("/users/{userId}/avatar", produces = ["image/jpeg"])
    fun avatar(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable userId: String,
    ): ResponseEntity<ByteArray> {
        val id =
            runCatching { UUID.fromString(userId) }.getOrNull()
                ?: throw ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND)
        return imageResponse(avatars.get(id))
    }
}
