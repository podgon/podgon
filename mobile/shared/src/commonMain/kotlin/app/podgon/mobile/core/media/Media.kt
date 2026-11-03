package app.podgon.mobile.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.podgon.contract.UserProfile
import app.podgon.mobile.core.network.ApiEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Адреса картинок (этап 8.3.4): абсолютные, на текущем адресе API (с учётом запасных, R2). Идентификатор
 * картинки в `?v=` меняется при каждой загрузке — кэш Coil не устаревает.
 */
class MediaUrls(
    private val endpoints: ApiEndpoints,
) {
    fun photo(
        reviewId: String,
        photoId: String?,
    ): String? = photoId?.let { "${endpoints.current}/api/v1/reviews/$reviewId/photo?v=$it" }

    fun avatar(
        userId: String,
        avatarId: String?,
    ): String? = avatarId?.let { "${endpoints.current}/api/v1/users/$userId/avatar?v=$it" }
}

/** Адреса картинок для экранов; в превью и тестах — нет картинок (аватар по умолчанию). */
val LocalMediaUrls = staticCompositionLocalOf<MediaUrls?> { null }

@Composable
fun avatarUrl(
    userId: String,
    avatarId: String?,
): String? = LocalMediaUrls.current?.avatar(userId, avatarId)

@Composable
fun photoUrl(
    reviewId: String,
    photoId: String?,
): String? = LocalMediaUrls.current?.photo(reviewId, photoId)

/** Свой профиль для верхней строки (аватар): обновляется при загрузке профиля и смене аватара. */
class MyProfile {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    fun set(profile: UserProfile?) {
        if (profile != null) _profile.value = profile
    }
}

/**
 * Выбор картинки из галереи. Возвращает JPEG, уже повёрнутый по EXIF и уменьшенный (длинная сторона не больше
 * [maxSide]): так меньше передаётся, а сервер всё равно перекодирует и уберёт метаданные.
 */
@Composable
expect fun rememberImagePicker(
    maxSide: Int = 1600,
    onPicked: (ByteArray) -> Unit,
): () -> Unit
