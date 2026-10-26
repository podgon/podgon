package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals

/** Выход из группы и удаление группы: судьба публикаций и рекомендаций. */
class LifecycleTest : GroupsTestBase() {
    private fun cat(
        access: String,
        groupId: String,
    ) = body(get("/groups/$groupId/categories", access))[0]["id"].asString()

    private fun review(
        access: String,
        title: String,
        vararg groups: String,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf(
                    "title" to title,
                    "rating" to 7,
                    "publications" to groups.map { mapOf("groupId" to it, "categoryId" to cat(access, it)) },
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun feedTitles(
        access: String,
        groupId: String,
    ) = body(get("/groups/$groupId/reviews", access))["items"].toList().map { it["title"].asString() }.toSet()

    @Test
    fun `выход с удалением своих публикаций оставляет рекомендацию автору`() {
        val admin = newUser("lc_admin1")
        val member = newUser("lc_member1")
        val g1 = createGroup(admin, "Первая")["id"].asString()
        val g2 = createGroup(member, "Вторая")["id"].asString()
        join(admin, g1, member)
        val both = review(member, "Везде", g1, g2)
        review(member, "Только здесь", g1)
        review(admin, "Админская", g1)

        assertEquals(2, body(get("/groups/$g1/leave-impact", member))["myPublications"].asInt())
        assertEquals(204, status(call(HttpMethod.POST, "/groups/$g1/leave?deleteMyReviews=true", member)))

        assertEquals(setOf("Админская"), feedTitles(admin, g1))
        assertEquals(200, status(get("/reviews/$both", member)))
        assertEquals(1, body(get("/reviews/$both", member))["publications"].size())
        assertEquals(2, body(get("/me/reviews", member))["items"].size())
    }

    @Test
    fun `выход без флага оставляет публикации в группе`() {
        val admin = newUser("lc_admin2")
        val member = newUser("lc_member2")
        val g = createGroup(admin)["id"].asString()
        join(admin, g, member)
        review(member, "Остаётся", g)
        assertEquals(204, status(call(HttpMethod.POST, "/groups/$g/leave", member)))
        assertEquals(setOf("Остаётся"), feedTitles(admin, g))
        assertEquals(404, status(get("/groups/$g/leave-impact", member)))
    }

    @Test
    fun `единственный админ не может выйти даже с удалением публикаций`() {
        val admin = newUser("lc_admin3")
        val member = newUser("lc_member3")
        val g = createGroup(admin)["id"].asString()
        join(admin, g, member)
        review(admin, "Моя", g)
        assertEquals("LAST_ADMIN", code(call(HttpMethod.POST, "/groups/$g/leave?deleteMyReviews=true", admin)))
        assertEquals(setOf("Моя"), feedTitles(admin, g), "откат: публикации на месте")
    }

    @Test
    fun `удаление группы возвращает рекомендации в личные`() {
        val admin = newUser("lc_admin4")
        val member = newUser("lc_member4")
        val g1 = createGroup(admin, "Первая")["id"].asString()
        val g2 = createGroup(admin, "Вторая")["id"].asString()
        join(admin, g1, member)
        val only = review(member, "Только в первой", g1)
        val shared = review(admin, "В обеих", g1, g2)

        val impact = body(get("/groups/$g1/delete-impact", admin))
        assertEquals(1, impact["exclusiveReviews"].asInt())
        assertEquals(2, impact["publications"].asInt())
        assertEquals(403, status(get("/groups/$g1/delete-impact", member)))

        assertEquals(204, status(delete("/groups/$g1?confirm=true", admin)))
        assertEquals(0, body(get("/reviews/$only", member))["publications"].size())
        assertEquals(1, body(get("/reviews/$shared", admin))["publications"].size())
    }

    @Test
    fun `удаление группы с удалением рекомендаций убирает только эксклюзивные`() {
        val admin = newUser("lc_admin5")
        val member = newUser("lc_member5")
        val g1 = createGroup(admin, "Первая")["id"].asString()
        val g2 = createGroup(admin, "Вторая")["id"].asString()
        join(admin, g1, member)
        val only = review(member, "Только в первой", g1)
        val shared = review(admin, "В обеих", g1, g2)

        assertEquals(1, body(get("/groups/$g1/delete-impact", admin))["exclusiveReviews"].asInt())
        assertEquals(400, status(delete("/groups/$g1?reviews=DELETE", admin)))
        assertEquals(204, status(delete("/groups/$g1?confirm=true&reviews=DELETE", admin)))
        assertEquals(404, status(get("/reviews/$only", member)))
        assertEquals(200, status(get("/reviews/$shared", admin)))
        assertEquals(setOf("В обеих"), feedTitles(admin, g2))
    }
}
