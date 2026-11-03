package app.podgon.media

import app.podgon.common.validationFailed
import app.podgon.contract.FieldError
import app.podgon.contract.FieldErrorReason
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Приём картинок: тело запроса читается не больше лимита, картинка (JPEG или PNG) декодируется после проверки
 * размеров, уменьшается и перекодируется в JPEG. Перекодирование убирает метаданные (EXIF с геопозицией);
 * поворот по EXIF клиент применяет сам перед загрузкой.
 */
@Component
class Images(
    private val props: MediaProperties,
) {
    /** Тело запроса целиком; больше лимита — 400 `VALIDATION_FAILED` (`image`, `TOO_LONG`). */
    fun read(request: HttpServletRequest): ByteArray {
        if (request.contentLengthLong > props.maxUploadBytes) throw invalid(FieldErrorReason.TOO_LONG)
        val bytes = request.inputStream.readNBytes(props.maxUploadBytes + 1)
        if (bytes.size > props.maxUploadBytes) throw invalid(FieldErrorReason.TOO_LONG)
        if (bytes.isEmpty()) throw invalid(FieldErrorReason.REQUIRED)
        return bytes
    }

    /** Фото рекомендации: длинная сторона не больше [MediaProperties.photoMaxSide]. */
    fun photo(raw: ByteArray): ByteArray {
        val image = decode(raw)
        val scale = min(1.0, props.photoMaxSide.toDouble() / max(image.width, image.height))
        val w = (image.width * scale).roundToInt().coerceAtLeast(1)
        val h = (image.height * scale).roundToInt().coerceAtLeast(1)
        return jpeg(draw(image, w, h, 0, 0, image.width, image.height))
    }

    /** Аватар: центральный квадрат, сторона [MediaProperties.avatarSide]. */
    fun avatar(raw: ByteArray): ByteArray {
        val image = decode(raw)
        val side = min(image.width, image.height)
        val x = (image.width - side) / 2
        val y = (image.height - side) / 2
        val out = min(props.avatarSide, side)
        return jpeg(draw(image, out, out, x, y, side, side))
    }

    private fun decode(raw: ByteArray): BufferedImage {
        val input = ImageIO.createImageInputStream(raw.inputStream()) ?: throw invalid(FieldErrorReason.INVALID_FORMAT)
        input.use {
            val reader =
                ImageIO.getImageReaders(input).asSequence().firstOrNull { r ->
                    r.formatName.lowercase() in FORMATS
                } ?: throw invalid(FieldErrorReason.INVALID_FORMAT)
            try {
                reader.input = input
                val pixels = reader.getWidth(0).toLong() * reader.getHeight(0)
                if (pixels > props.maxPixels) throw invalid(FieldErrorReason.TOO_LONG)
                return reader.read(0)
            } catch (
                @Suppress("SwallowedException") e: java.io.IOException,
            ) {
                throw invalid(FieldErrorReason.INVALID_FORMAT)
            } finally {
                reader.dispose()
            }
        }
    }

    @Suppress("LongParameterList")
    private fun draw(
        source: BufferedImage,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        sw: Int,
        sh: Int,
    ): BufferedImage {
        // RGB без прозрачности: у PNG с альфа-каналом фон белый
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, width, height)
            g.drawImage(source, 0, 0, width, height, x, y, x + sw, y + sh, null)
        } finally {
            g.dispose()
        }
        return target
    }

    private fun jpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = JPEG_QUALITY
                    }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    private fun invalid(reason: FieldErrorReason) = validationFailed(listOf(FieldError("image", reason)))

    private companion object {
        val FORMATS = setOf("jpeg", "jpg", "png")
        const val JPEG_QUALITY = 0.85f
    }
}

/**
 * Ответ с картинкой. Адрес содержит идентификатор файла (`?v=`), при замене картинки он меняется, поэтому
 * кэшировать можно надолго, но только на устройстве (`private`: картинки доступны не всем).
 */
fun imageResponse(bytes: ByteArray): org.springframework.http.ResponseEntity<ByteArray> =
    org.springframework.http.ResponseEntity
        .ok()
        .contentType(org.springframework.http.MediaType.IMAGE_JPEG)
        .cacheControl(
            org.springframework.http.CacheControl
                .maxAge(java.time.Duration.ofDays(365))
                .cachePrivate(),
        ).body(bytes)
