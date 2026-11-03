package app.podgon.notifications

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Уведомления (этап 8.3.5): входящие приглашения, подписки на фильтры, настройки, прочтение. */
class NotificationsTest : GroupsTestBase() {
    private fun notifications(access: String): JsonNode = body(get("/me/notifications", access))

    private fun kinds(access: String) = notifications(access)["items"].toList().map { it["kind"].asString() }

    private fun review(
        access: String,
        title: String,
        groupIds: List<String>,
        rating: Int = 9,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf(
                    "title" to title,
                    "rating" to rating,
                    "categoryId" to category("books.sf"),
                    "groupIds" to groupIds,
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun subscribe(
        access: String,
        name: String,
        definition: Map<String, Any?>,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/me/filters",
                access,
                mapOf("kind" to "FILTER", "name" to name, "definition" to definition, "subscribed" to true),
            )
        check(status(r) == 201) { r.response.contentAsString }
        assertTrue(body(r)["subscribed"].asBoolean())
        return body(r)["id"].asString()
    }

    @Test
    fun `адресное приглашение приходит уведомлением и принимается без кода`() {
        val admin = newUser("ntf_admin")
        val guest = newUser("ntf_guest")
        val stranger = newUser("ntf_stranger")
        val g = createGroup(admin, "Друзья")["id"].asString()
        invite(admin, g, email = "ntf_guest@example.com")

        val page = notifications(guest)
        assertEquals(1, page["unread"].asInt())
        val item = page["items"][0]
        assertEquals("INVITATION", item["kind"].asString())
        assertEquals("Друзья", item["groupName"].asString())
        val invitationId = item["invitationId"].asString()
        val incoming = body(get("/me/invitations", guest)).toList()
        assertEquals(listOf(invitationId), incoming.map { it["id"].asString() })
        assertEquals(0, body(get("/me/invitations", stranger)).size())

        // чужое адресное приглашение по идентификатору не принять
        val foreign = call(HttpMethod.POST, "/invitations/accept", stranger, mapOf("invitationId" to invitationId))
        assertEquals("INVALID_INVITATION", code(foreign))
        val preview = call(HttpMethod.POST, "/invitations/preview", guest, mapOf("invitationId" to invitationId))
        assertEquals("Друзья", body(preview)["groupName"].asString())
        val sharePreview =
            call(
                HttpMethod.POST,
                "/invitations/share-preview",
                guest,
                mapOf("invitationId" to invitationId, "share" to mapOf("mode" to "ALL")),
            )
        assertEquals(200, status(sharePreview))
        val joined = call(HttpMethod.POST, "/invitations/accept", guest, mapOf("invitationId" to invitationId))
        assertEquals(200, status(joined), joined.response.contentAsString)
        assertEquals(0, body(get("/me/invitations", guest)).size(), "уже участник")

        // приглашение по коду по идентификатору не принимается: код нужно знать
        invite(admin, g)
        val codeId =
            body(
                get("/groups/$g/invitations", admin),
            ).toList().first { !it.hasNonNull("email") }["id"].asString()
        assertEquals(
            "INVALID_INVITATION",
            code(
                call(
                    HttpMethod.POST,
                    "/invitations/accept",
                    stranger,
                    mapOf(
                        "invitationId" to codeId,
                    ),
                ),
            ),
        )
        assertEquals(
            "INVALID_INVITATION",
            code(call(HttpMethod.POST, "/invitations/accept", stranger, emptyMap<String, Any>())),
        )
    }

    @Test
    fun `подписка на фильтр уведомляет о новой подходящей рекомендации один раз`() {
        val author = newUser("ntf_author")
        val reader = newUser("ntf_reader")
        val g1 = createGroup(author, "Первая")["id"].asString()
        val g2 = createGroup(author, "Вторая")["id"].asString()
        join(author, g1, reader)
        join(author, g2, reader)
        subscribe(
            reader,
            "Хорошие книги",
            mapOf("v" to 1, "categoryIds" to listOf(category("books")), "minRating" to 8),
        )
        subscribe(author, "Своё", mapOf("v" to 1))

        review(author, "Слабая", listOf(g1), rating = 5)
        assertEquals(emptyList(), kinds(reader), "не подходит по оценке")
        val id = review(author, "Дюна", listOf(g1))
        val page = notifications(reader)
        assertEquals(listOf("FILTER_MATCH"), kinds(reader))
        assertEquals("Дюна", page["items"][0]["reviewTitle"].asString())
        assertEquals("Хорошие книги", page["items"][0]["filterName"].asString())
        assertEquals(id, page["items"][0]["reviewId"].asString())
        assertEquals(emptyList(), kinds(author), "о своих рекомендациях не уведомляем")

        // публикация той же рекомендации во вторую группу второго уведомления не даёт
        assertEquals(
            200,
            status(call(HttpMethod.PUT, "/reviews/$id/groups", author, mapOf("groupIds" to listOf(g1, g2)))),
        )
        assertEquals(1, notifications(reader)["items"].size())

        assertEquals(1, body(get("/me/notifications/unread", reader))["count"].asInt())
        assertEquals(204, status(call(HttpMethod.POST, "/me/notifications/read", reader, mapOf("ids" to null))))
        assertEquals(0, body(get("/me/notifications/unread", reader))["count"].asInt())
        assertTrue(notifications(reader)["items"][0]["read"].asBoolean())
    }

    @Test
    fun `выключенные в настройках уведомления не создаются`() {
        val author = newUser("ntf_author3")
        val reader = newUser("ntf_reader3")
        val g = createGroup(author)["id"].asString()
        join(author, g, reader)
        subscribe(reader, "Всё", mapOf("v" to 1))
        val saved =
            call(
                HttpMethod.PUT,
                "/me/notification-settings",
                reader,
                mapOf(
                    "invitations" to false,
                    "filterMatches" to false,
                ),
            )
        assertEquals(false, body(saved)["filterMatches"].asBoolean())
        review(author, "Тихо", listOf(g))
        invite(author, createGroup(author, "Другая")["id"].asString(), email = "ntf_reader3@example.com")
        assertEquals(emptyList(), kinds(reader))
        assertEquals(1, body(get("/me/invitations", reader)).size(), "в списке входящих приглашение всё равно есть")

        val filter = body(get("/me/filters", reader)).toList().single()
        val off = call(HttpMethod.PATCH, "/me/filters/${filter["id"].asString()}", reader, mapOf("subscribed" to false))
        assertTrue(body(off).get("subscribed")?.asBoolean() != true)
    }
}
