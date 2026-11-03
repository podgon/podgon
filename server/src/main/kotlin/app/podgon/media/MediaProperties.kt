package app.podgon.media

import org.springframework.boot.context.properties.ConfigurationProperties

/** Хранилище файлов и ограничения картинок. */
@ConfigurationProperties("podgon.media")
data class MediaProperties(
    /** Каталог файлов; в контейнере — отдельный том. */
    val dir: String = "./data/media",
    /** Наибольший размер загружаемого файла, байт. */
    val maxUploadBytes: Int = 10 * 1024 * 1024,
    /** Наибольшее число пикселей исходной картинки (защита от «бомб» при декодировании). */
    val maxPixels: Long = 50_000_000,
    /** Длинная сторона фото рекомендации после уменьшения, px. */
    val photoMaxSide: Int = 1600,
    /** Сторона квадратного аватара, px. */
    val avatarSide: Int = 256,
)
