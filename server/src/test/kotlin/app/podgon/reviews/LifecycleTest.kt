package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals

/** Выход из группы и удаление группы: судьба публикаций и рекомендаций. */
class LifecycleTest : GroupsTestBase() {
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
                    "categoryId" to category("food.coffee"),
                    "groupIds" to groups.toList(),
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
        assertEquals(1, body(get("/me/reviews", member))["items"].size())
        assertEquals(setOf("Только здесь"), feedTitles(member, personalGroup(member)), "нигде не видна — стала личной")
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
        assertEquals(true, body(get("/reviews/$only", member))["personal"].asBoolean())
        assertEquals(setOf("Только в первой"), feedTitles(member, personalGroup(member)))
        assertEquals(1, body(get("/reviews/$shared", admin))["publications"].size())
        assertEquals(emptySet(), feedTitles(admin, personalGroup(admin)), "видная ещё где-то не стала личной")
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

    /** Методов избранного ещё нет (backlog): кладём рекомендацию в «Избранное» напрямую. */
    private fun favorite(
        access: String,
        reviewId: String,
    ) {
        jdbc
            .sql("insert into review_groups (review_id, group_id, shared_at) values (:r, :g, now())")
            .param("r", java.util.UUID.fromString(reviewId))
            .param("g", java.util.UUID.fromString(favoritesGroup(access)))
            .update()
    }

    @Test
    fun `чужая рекомендация уходит из Избранного когда владелец её больше не видит`() {
        val author = newUser("lc_author6")
        val fan = newUser("lc_fan6")
        val g1 = createGroup(author, "Первая")["id"].asString()
        val g2 = createGroup(author, "Вторая")["id"].asString()
        val g3 = createGroup(author, "Третья")["id"].asString()
        listOf(g1, g2, g3).forEach { join(author, it, fan) }
        val inTwo = review(author, "В двух", g1, g2)
        val unpublished = review(author, "Снимут", g3)
        val deletedGroup = review(author, "Группу удалят", g3)
        val own = review(fan, "Своя", g1)
        listOf(inTwo, unpublished, deletedGroup, own).forEach { favorite(fan, it) }
        val favorites = favoritesGroup(fan)

        // выход из одной из групп: «В двух» ещё видна через вторую
        assertEquals(204, status(call(HttpMethod.POST, "/groups/$g1/leave", fan)))
        assertEquals(setOf("В двух", "Снимут", "Группу удалят", "Своя"), feedTitles(fan, favorites))

        // автор снял публикацию — рекомендация больше не видна
        val reset = call(HttpMethod.PUT, "/reviews/$unpublished/groups", author, mapOf("groupIds" to listOf(g1)))
        assertEquals(200, status(reset))
        assertEquals(setOf("В двух", "Группу удалят", "Своя"), feedTitles(fan, favorites))

        // удаление группы
        assertEquals(204, status(delete("/groups/$g3?confirm=true", author)))
        assertEquals(setOf("В двух", "Своя"), feedTitles(fan, favorites))

        // администратор удалил участника: свои рекомендации остаются в его «Избранном»
        assertEquals(204, status(delete("/groups/$g2/members/${userId(fan)}", author)))
        assertEquals(setOf("Своя"), feedTitles(fan, favorites))
    }

    @Test
    fun `системными группами управлять нельзя`() {
        val u = newUser("lc_sys7")
        val other = newUser("lc_sys7o")
        val personal = personalGroup(u)
        val favorites = favoritesGroup(u)
        assertEquals(0, body(get("/groups", u)).size(), "системные группы не в списке")
        for (g in listOf(personal, favorites)) {
            assertEquals("SYSTEM_GROUP", code(get("/groups/$g", u)))
            assertEquals("SYSTEM_GROUP", code(get("/groups/$g/members", u)))
            assertEquals("SYSTEM_GROUP", code(call(HttpMethod.PATCH, "/groups/$g", u, mapOf("name" to "Новое"))))
            assertEquals("SYSTEM_GROUP", code(call(HttpMethod.POST, "/groups/$g/leave", u)))
            assertEquals("SYSTEM_GROUP", code(delete("/groups/$g?confirm=true", u)))
            assertEquals(
                "SYSTEM_GROUP",
                code(call(HttpMethod.POST, "/groups/$g/invitations", u, mapOf("maxUses" to 1))),
            )
            assertEquals(404, status(get("/groups/$g", other)), "чужая системная группа не раскрывается")
        }
        assertEquals(200, status(get("/groups/$personal/reviews", u)))
    }
}
