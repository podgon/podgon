package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Избранное (этап 8.3.2): добавление и удаление, признак `favorite`, лента «Избранного», чистка Ф1. */
class FavoritesTest : GroupsTestBase() {
    /** Пустые и ложные поля в ответе не передаются: нет поля — не в избранном. */
    private fun tools.jackson.databind.JsonNode.fav() = get("favorite")?.asBoolean() ?: false

    private fun created(
        access: String,
        title: String,
        groupIds: List<String>,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf("title" to title, "rating" to 8, "categoryId" to category("books.sf"), "groupIds" to groupIds),
            )
        check(status(r) == 201) { r.response.contentAsString }
        clock.advance(Duration.ofSeconds(1))
        return body(r)["id"].asString()
    }

    private fun favorite(
        access: String,
        reviewId: String,
    ) = status(call(HttpMethod.PUT, "/me/favorites/$reviewId", access))

    private fun unfavorite(
        access: String,
        reviewId: String,
    ) = status(delete("/me/favorites/$reviewId", access))

    private fun titles(
        access: String,
        groupId: String,
    ) = body(get("/groups/$groupId/reviews", access))["items"].toList().map { it["title"].asString() }

    @Test
    fun `чужая видимая рекомендация добавляется в избранное и отмечается сердечком`() {
        val author = newUser("fav_author")
        val member = newUser("fav_member")
        val g = createGroup(author)["id"].asString()
        join(author, g, member)
        val first = created(author, "Первая", listOf(g))
        val second = created(author, "Вторая", listOf(g))

        assertEquals(204, favorite(member, first))
        assertEquals(204, favorite(member, first), "повторное добавление ничего не меняет")
        clock.advance(Duration.ofSeconds(1))
        assertEquals(204, favorite(member, second))
        val favorites = favoritesGroup(member)
        assertEquals(listOf("Вторая", "Первая"), titles(member, favorites), "новые добавления сверху")

        val feed =
            body(get("/groups/$g/reviews", member))["items"].toList().associate {
                it["title"].asString() to it.fav()
            }
        assertEquals(mapOf("Первая" to true, "Вторая" to true), feed)
        assertTrue(body(get("/reviews/$first", member)).fav())
        assertFalse(body(get("/reviews/$first", author)).fav(), "у автора своё избранное")
        assertEquals(
            0,
            body(get("/reviews/$first", member))["publications"].toList().count {
                it["groupId"].asString() ==
                    favorites
            },
        )

        assertEquals(204, unfavorite(member, first))
        assertEquals(204, unfavorite(member, first), "повторное удаление ничего не меняет")
        assertEquals(listOf("Вторая"), titles(member, favorites))
        assertFalse(body(get("/reviews/$first", member)).fav())
    }

    @Test
    fun `невидимую рекомендацию добавить нельзя а своя личная добавляется`() {
        val author = newUser("fav_author2")
        val outsider = newUser("fav_out2")
        val g = createGroup(author)["id"].asString()
        val shared = created(author, "Групповая", listOf(g))
        val personal = created(author, "Личная", emptyList())
        assertEquals(404, favorite(outsider, shared))
        assertEquals(404, favorite(outsider, "00000000-0000-0000-0000-000000000000"))
        assertEquals(204, favorite(author, personal))
        assertEquals(listOf("Личная"), titles(author, favoritesGroup(author)))
    }

    @Test
    fun `после выхода из группы чужая рекомендация пропадает из избранного`() {
        val author = newUser("fav_author3")
        val member = newUser("fav_member3")
        val g = createGroup(author)["id"].asString()
        join(author, g, member)
        val id = created(author, "Книга", listOf(g))
        assertEquals(204, favorite(member, id))
        assertEquals(204, status(call(HttpMethod.POST, "/groups/$g/leave", member)))
        assertEquals(emptyList(), titles(member, favoritesGroup(member)))
    }
}
