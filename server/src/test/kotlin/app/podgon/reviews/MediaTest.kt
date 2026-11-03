package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import app.podgon.identity.domain.AvatarService
import app.podgon.media.MediaKeys
import app.podgon.media.MediaStorage
import app.podgon.reviews.domain.PhotoCleanup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Фото рекомендаций и аватары (этап 8.3.4): загрузка, права, уменьшение, удаление метаданных, чистка. */
class MediaTest : GroupsTestBase() {
    @Autowired
    lateinit var storage: MediaStorage

    @Autowired
    lateinit var photoCleanup: PhotoCleanup

    @Autowired
    lateinit var avatars: AvatarService

    private fun image(
        width: Int,
        height: Int,
        format: String = "png",
    ): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.ORANGE
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
    }

    /** JPEG со вставленным блоком EXIF (APP1) сразу после SOI: в нём «геопозиция», которая не должна дойти до других. */
    private fun jpegWithExif(): ByteArray {
        val plain = image(400, 300, "jpg")
        val payload = "Exif\u0000\u0000SECRET-GPS".toByteArray(Charsets.ISO_8859_1)
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return plain.copyOfRange(0, 2) + app1 + plain.copyOfRange(2, plain.size)
    }

    private fun upload(
        path: String,
        access: String,
        bytes: ByteArray,
        type: String = "image/png",
    ): MvcResult =
        mockMvc
            .perform(
                request(HttpMethod.PUT, "/api/v1$path")
                    .header("Authorization", "Bearer $access")
                    .contentType(type)
                    .content(bytes),
            ).andReturn()

    private fun created(
        access: String,
        groupIds: List<String>,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf("title" to "С фото", "rating" to 8, "categoryId" to category("books.sf"), "groupIds" to groupIds),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun decoded(result: MvcResult): BufferedImage =
        ImageIO.read(result.response.contentAsByteArray.inputStream())

    @Test
    fun `фото уменьшается видно участникам и меняется только автором`() {
        val author = newUser("media_author")
        val member = newUser("media_member")
        val outsider = newUser("media_out")
        val g = createGroup(author)["id"].asString()
        join(author, g, member)
        val id = created(author, listOf(g))

        assertEquals(403, status(upload("/reviews/$id/photo", member, image(10, 10))))
        assertEquals(404, status(upload("/reviews/$id/photo", outsider, image(10, 10))))
        val details = upload("/reviews/$id/photo", author, image(3200, 1600))
        assertEquals(200, status(details), details.response.contentAsString)
        val photoId = body(details)["photoId"].asString()

        val seen = get("/reviews/$id/photo?v=$photoId", member)
        assertEquals(200, status(seen))
        assertEquals("image/jpeg", seen.response.contentType)
        assertTrue(seen.response.getHeader("Cache-Control")!!.contains("private"))
        val img = decoded(seen)
        assertEquals(1600, img.width)
        assertEquals(800, img.height)
        assertEquals(photoId, body(get("/reviews/$id", member))["photoId"].asString())
        assertEquals(photoId, body(get("/groups/$g/reviews", member))["items"][0]["photoId"].asString())
        assertEquals(404, status(get("/reviews/$id/photo", outsider)))

        // новое фото заменяет прежнее: файл прежнего удаляется сразу
        val second = body(upload("/reviews/$id/photo", author, image(100, 100)))["photoId"].asString()
        assertNull(storage.get(MediaKeys.photo(photoId)))
        assertNotNull(storage.get(MediaKeys.photo(second)))

        val removed = call(HttpMethod.DELETE, "/reviews/$id/photo", author)
        assertEquals(200, status(removed))
        assertNull(body(removed).get("photoId"))
        assertEquals(404, status(get("/reviews/$id/photo", member)))
        assertNull(storage.get(MediaKeys.photo(second)))
    }

    @Test
    fun `метаданные вырезаются а не картинка отклоняется`() {
        val author = newUser("media_exif")
        val id = created(author, emptyList())
        val bytes = jpegWithExif()
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("SECRET-GPS"))
        assertEquals(200, status(upload("/reviews/$id/photo", author, bytes, "image/jpeg")))
        val stored = get("/reviews/$id/photo", author).response.contentAsByteArray
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("SECRET-GPS"), "EXIF не должен сохраниться")

        val bad = upload("/reviews/$id/photo", author, "not an image".toByteArray())
        assertEquals(400, status(bad))
        assertEquals("image", body(bad)["errors"][0]["field"].asString())
        // неразрешённый тип — запрос заведомо вне контракта
        invalidRequests { assertEquals(415, status(upload("/reviews/$id/photo", author, image(10, 10), "image/gif"))) }
    }

    @Test
    fun `аватар квадратный виден другим и в карточках`() {
        val author = newUser("media_ava")
        val member = newUser("media_ava_m")
        val g = createGroup(author)["id"].asString()
        join(author, g, member)
        val id = created(author, listOf(g))

        val profile = upload("/me/avatar", author, image(600, 400))
        assertEquals(200, status(profile), profile.response.contentAsString)
        val avatarId = body(profile)["avatarId"].asString()
        val authorId = userId(author)
        val img = decoded(get("/users/$authorId/avatar?v=$avatarId", member))
        // старый клиент слал Accept: application/json (вне контракта) — картинка всё равно приходит
        val asJsonClient =
            invalidRequests {
                mockMvc
                    .perform(
                        request(HttpMethod.GET, "/api/v1/users/$authorId/avatar")
                            .header("Authorization", "Bearer $member")
                            .header("Accept", "application/json"),
                    ).andReturn()
            }
        assertEquals(200, status(asJsonClient))
        assertEquals("image/jpeg", asJsonClient.response.contentType)
        assertEquals(256, img.width)
        assertEquals(256, img.height)
        assertEquals(avatarId, body(get("/reviews/$id", member))["author"]["avatarId"].asString())
        val members = body(get("/groups/$g/members", member)).toList()
        assertEquals(avatarId, members.first { it["userId"].asString() == authorId }["avatarId"].asString())

        assertEquals(200, status(call(HttpMethod.DELETE, "/me/avatar", author)))
        assertEquals(404, status(get("/users/$authorId/avatar", member)))
        assertNull(storage.get(MediaKeys.avatar(avatarId)))
    }

    @Test
    fun `ночная чистка удаляет только старые файлы без ссылок`() {
        val author = newUser("media_clean")
        val id = created(author, emptyList())
        val kept = body(upload("/reviews/$id/photo", author, image(20, 20)))["photoId"].asString()
        val orphan = "00000000-0000-0000-0000-0000000000aa"
        val fresh = "00000000-0000-0000-0000-0000000000bb"
        storage.put(MediaKeys.photo(orphan), byteArrayOf(1))
        storage.put(MediaKeys.photo(fresh), byteArrayOf(1))
        val old = FileTime.from(clock.instant().minus(Duration.ofDays(2)))
        val dir = Path.of("build/test-media")
        listOf(orphan, kept).forEach { Files.setLastModifiedTime(dir.resolve(MediaKeys.photo(it)), old) }

        photoCleanup.run()
        assertNull(storage.get(MediaKeys.photo(orphan)))
        assertNotNull(storage.get(MediaKeys.photo(kept)), "есть ссылка")
        assertNotNull(storage.get(MediaKeys.photo(fresh)), "моложе суток")

        // удаление рекомендации убирает файл сразу
        assertEquals(204, status(delete("/reviews/$id", author)))
        assertNull(storage.get(MediaKeys.photo(kept)))
        avatars.cleanup()
    }
}
