package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Удаление аккаунта: подтверждение кодом, блокировка единственного админа, судьба рекомендаций и данных. */
class AccountDeletionTest : GroupsTestBase() {
    private class Account(
        val name: String,
        val tokens: tools.jackson.databind.JsonNode,
    ) {
        val access: String = tokens["accessToken"].asString()
        val email: String = "$name@example.com"
    }

    private fun account(name: String) = Account(name, registerUser("$name@example.com", name))

    private fun cat(
        access: String,
        groupId: String,
    ) = body(get("/groups/$groupId/categories", access))[0]["id"].asString()

    private fun review(
        access: String,
        title: String,
        groupId: String? = null,
    ): String {
        val publications =
            if (groupId ==
                null
            ) {
                emptyList()
            } else {
                listOf(mapOf("groupId" to groupId, "categoryId" to cat(access, groupId)))
            }
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf("title" to title, "rating" to 6, "tags" to listOf("тест"), "publications" to publications),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun requestCode(a: Account): String {
        val r = call(HttpMethod.POST, "/me/deletion/code", a.access)
        check(status(r) == 204) { r.response.contentAsString }
        return latestCode(a.email)
    }

    private fun deleteAccount(
        a: Account,
        code: String,
        deleteReviews: Boolean = false,
    ) = call(HttpMethod.POST, "/me/deletion", a.access, mapOf("code" to code, "deleteReviews" to deleteReviews))

    @Test
    fun `без верного кода аккаунт не удаляется`() {
        val a = account("ad_code1")
        assertEquals("INVALID_CODE", code(deleteAccount(a, "123456")))
        val sent = requestCode(a)
        assertEquals("INVALID_CODE", code(deleteAccount(a, if (sent == "000000") "000001" else "000000")))
        assertEquals(200, status(get("/me", a.access)))
        assertEquals(204, status(deleteAccount(a, sent)))
        assertEquals(401, status(get("/me", a.access)))
    }

    @Test
    fun `письмо с кодом на языке пользователя`() {
        val a = account("ad_mail1")
        requestCode(a)
        val template =
            jdbc
                .sql("select template from mail_outbox where to_address = :e order by created_at desc limit 1")
                .param("e", a.email)
                .query(String::class.java)
                .single()
        assertEquals("ACCOUNT_DELETION_CODE", template)
    }

    @Test
    fun `единственный админ группы с участниками не может удалить аккаунт`() {
        val admin = account("ad_admin2")
        val member = account("ad_member2")
        val g = createGroup(admin.access, "Клуб")["id"].asString()
        join(admin.access, g, member.access)
        review(admin.access, "Моя", g)

        val impact = body(get("/me/delete-impact", admin.access))
        assertEquals(1, impact["reviews"].asInt())
        assertEquals(1, impact["publishedReviews"].asInt())
        assertEquals(listOf(g), impact["blockingGroups"].toList().map { it["id"].asString() })

        assertEquals("LAST_ADMIN", code(deleteAccount(admin, requestCode(admin))))
        assertEquals(200, status(get("/groups/$g", admin.access)), "откат: членство на месте")
        assertEquals(1, body(get("/groups/$g/reviews", admin.access))["items"].size())

        // после передачи прав удаление проходит
        setRole(admin.access, g, userId(member.access), roleId(admin.access, g, "ADMIN"))
        assertEquals(0, body(get("/me/delete-impact", admin.access))["blockingGroups"].size())
        clock.advance(java.time.Duration.ofSeconds(61))
        assertEquals(204, status(deleteAccount(admin, requestCode(admin))))
        assertEquals(1, members(member.access, g).size())
    }

    @Test
    fun `рекомендации с публикациями переходят удалённому пользователю а личные удаляются`() {
        val admin = account("ad_admin3")
        val leaver = account("ad_leaver3")
        val g = createGroup(admin.access, "Клуб")["id"].asString()
        join(admin.access, g, leaver.access)
        val published = review(leaver.access, "Опубликованная", g)
        val personal = review(leaver.access, "Личная")
        val leaverId = userId(leaver.access)

        assertEquals(2, body(get("/me/delete-impact", leaver.access))["reviews"].asInt())
        assertEquals(204, status(deleteAccount(leaver, requestCode(leaver))))

        val card = body(get("/reviews/$published", admin.access))
        assertEquals("deleted_user", card["author"]["username"].asString())
        assertEquals(404, status(get("/reviews/$personal", admin.access)))
        assertEquals(1, members(admin.access, g).size())
        assertTrue(members(admin.access, g).none { it["userId"].asString() == leaverId })

        val row =
            jdbc
                .sql("select status, deleted_at is not null as gone, email::text as email from users where id = :id")
                .param("id", java.util.UUID.fromString(leaverId))
                .query { rs, _ -> Triple(rs.getString("status"), rs.getBoolean("gone"), rs.getString("email")) }
                .single()
        assertEquals("DELETED", row.first)
        assertTrue(row.second)
        assertNotEquals(leaver.email, row.third)
    }

    @Test
    fun `удаление вместе со всеми рекомендациями`() {
        val admin = account("ad_admin4")
        val leaver = account("ad_leaver4")
        val g = createGroup(admin.access, "Клуб")["id"].asString()
        join(admin.access, g, leaver.access)
        val published = review(leaver.access, "Опубликованная", g)
        review(admin.access, "Админская", g)

        assertEquals(204, status(deleteAccount(leaver, requestCode(leaver), deleteReviews = true)))
        assertEquals(404, status(get("/reviews/$published", admin.access)))
        assertEquals(1, body(get("/groups/$g/reviews", admin.access))["items"].size())
    }

    @Test
    fun `группа где пользователь один удаляется вместе с ним`() {
        val a = account("ad_solo5")
        val g = createGroup(a.access, "Одиночная")["id"].asString()
        val onlyHere = review(a.access, "Только здесь", g)

        assertEquals(204, status(deleteAccount(a, requestCode(a))))
        val groups = jdbc.sql("select count(*) from groups where id = :id").param("id", java.util.UUID.fromString(g))
        assertEquals(0, groups.query(Int::class.java).single())
        val reviews =
            jdbc
                .sql(
                    "select count(*) from reviews where id = :id",
                ).param("id", java.util.UUID.fromString(onlyHere))
        assertEquals(0, reviews.query(Int::class.java).single(), "личная после удаления группы — удаляется с аккаунтом")
    }

    @Test
    fun `сессии завершаются а адрес освобождается`() {
        val a = account("ad_free6")
        assertEquals(204, status(deleteAccount(a, requestCode(a))))
        assertEquals(401, status(post("/auth/refresh", mapOf("refreshToken" to a.tokens["refreshToken"].asString()))))
        clock.advance(java.time.Duration.ofSeconds(61))
        val again = registerUser(a.email, "ad_free6b")
        assertEquals("ad_free6b", body(get("/me", again["accessToken"].asString()))["username"].asString())
    }

    @Test
    fun `страница удаления аккаунта открыта без входа`() {
        val short = mockMvc.perform(MockMvcRequestBuilders.get("/account-deletion")).andReturn()
        assertEquals(200, short.response.status)
        assertEquals("/account-deletion.html", short.response.forwardedUrl)
        val page = mockMvc.perform(MockMvcRequestBuilders.get("/account-deletion.html")).andReturn()
        assertEquals(200, page.response.status)
        val html = page.response.getContentAsString(Charsets.UTF_8)
        assertTrue(html.contains("Удаление аккаунта") && html.contains("Deleting your Podgon account"))
    }
}
