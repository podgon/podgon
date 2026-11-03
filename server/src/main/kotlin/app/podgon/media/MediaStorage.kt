package app.podgon.media

import java.time.Instant

/**
 * Публичный порт хранилища файлов (фото рекомендаций, аватары). Сейчас — каталог на диске за backend
 * (`docs/plan.md`, этап 8.3), позже можно заменить на S3-совместимое хранилище без изменения API.
 * Ключ — относительный путь вида `photos/<id>.jpg`.
 */
interface MediaStorage {
    fun put(
        key: String,
        bytes: ByteArray,
    )

    fun get(key: String): ByteArray?

    fun delete(key: String)

    /** Ключи под [prefix] и время их записи (для ночной чистки файлов без ссылок). */
    fun list(prefix: String): List<StoredFile>
}

data class StoredFile(
    val key: String,
    val modifiedAt: Instant,
)

/** Ключ фото рекомендации и аватара по их идентификатору (случайный UUID, меняется при каждой загрузке). */
object MediaKeys {
    const val PHOTOS = "photos/"
    const val AVATARS = "avatars/"

    fun photo(id: String) = "$PHOTOS$id.jpg"

    fun avatar(id: String) = "$AVATARS$id.jpg"

    /** Идентификатор из ключа (`photos/<id>.jpg` → `<id>`). */
    fun idOf(key: String) = key.substringAfterLast('/').removeSuffix(".jpg")
}
