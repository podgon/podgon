package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals

/** Перенос групповых рекомендаций при вступлении в группу: правила по категориям набора и тегам. */
class SharingTest : GroupsTestBase() {
    private fun review(
        access: String,
        title: String,
        groupId: String? = null,
        categoryId: String = category("books.sf"),
        tags: List<String> = emptyList(),
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf(
                    "title" to title,
                    "rating" to 8,
                    "categoryId" to categoryId,
                    "tags" to tags,
                    "groupIds" to listOfNotNull(groupId),
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun rule(
        mode: String,
        categoryIds: List<String> = emptyList(),
        tags: List<String> = emptyList(),
    ) = mapOf("mode" to mode, "categoryIds" to categoryIds, "tags" to tags)

    private fun preview(
        access: String,
        token: String,
        rule: Map<String, Any>,
    ) = call(HttpMethod.POST, "/invitations/share-preview", access, mapOf("token" to token, "share" to rule))

    private fun joinWith(
        access: String,
        token: String,
        rule: Map<String, Any>?,
    ) = call(
        HttpMethod.POST,
        "/invitations/accept",
        access,
        mapOf("token" to token, "share" to rule).filterValues { it != null },
    )

    private fun titles(
        access: String,
        groupId: String,
    ): Set<String> =
        body(get("/groups/$groupId/reviews?limit=50", access))["items"]
            .toList()
            .map { it["title"].asString() }
            .toSet()

    /** Пользователь с группой-источником и принимающая группа, куда его зовёт админ. */
    private class Setup(
        val user: String,
        val source: String,
        val target: String,
        val token: String,
        val admin: String,
    )

    private fun setup(name: String): Setup {
        val user = newUser("${name}_u")
        val admin = newUser("${name}_a")
        val source = createGroup(user, "Источник")["id"].asString()
        val target = createGroup(admin, "Приёмник")["id"].asString()
        return Setup(user, source, target, invite(admin, target), admin)
    }

    @Test
    fun `переносятся все групповые рекомендации а личные нет`() {
        val s = setup("sh_all")
        val dune = review(s.user, "Дюна", s.source)
        review(s.user, "Личная заметка")

        val p = body(preview(s.user, s.token, rule("ALL")))
        assertEquals(1, p["total"].asInt())
        assertEquals(listOf(dune), p["items"].toList().map { it["reviewId"].asString() })
        assertEquals(category("books.sf"), p["items"][0]["categoryId"].asString())
        assertEquals(emptySet(), titles(s.admin, s.target), "предпросмотр ничего не меняет")

        assertEquals(200, status(joinWith(s.user, s.token, rule("ALL"))))
        assertEquals(setOf("Дюна"), titles(s.admin, s.target))
        assertEquals(setOf("Личная заметка"), titles(s.user, personalGroup(s.user)), "личная осталась личной")
    }

    @Test
    fun `корень категории включает подкатегории`() {
        val s = setup("sh_cat")
        review(s.user, "В корне", s.source, category("books"))
        review(s.user, "В подкатегории", s.source, category("books.sf"))
        review(s.user, "В другой", s.source, category("film.movies"))

        val root = listOf(category("books"))
        assertEquals(2, body(preview(s.user, s.token, rule("CATEGORIES", root)))["total"].asInt())
        val child = rule("CATEGORIES", listOf(category("books.sf")))
        assertEquals(1, body(preview(s.user, s.token, child))["total"].asInt())
        assertEquals(1, body(preview(s.user, s.token, rule("CATEGORIES_EXCEPT", root)))["total"].asInt())

        assertEquals(200, status(joinWith(s.user, s.token, rule("CATEGORIES", root))))
        assertEquals(setOf("В корне", "В подкатегории"), titles(s.user, s.target))
    }

    @Test
    fun `теги включают и исключают`() {
        val s = setup("sh_tag")
        review(s.user, "Фантастика", s.source, tags = listOf("scifi"))
        review(s.user, "Драма", s.source, tags = listOf("drama"))
        review(s.user, "Без тегов", s.source)

        assertEquals(1, body(preview(s.user, s.token, rule("TAGS", tags = listOf("#SciFi"))))["total"].asInt())
        assertEquals(2, body(preview(s.user, s.token, rule("TAGS", tags = listOf("scifi", "drama"))))["total"].asInt())
        assertEquals(2, body(preview(s.user, s.token, rule("TAGS_EXCEPT", tags = listOf("scifi"))))["total"].asInt())

        assertEquals(200, status(joinWith(s.user, s.token, rule("TAGS_EXCEPT", tags = listOf("scifi")))))
        assertEquals(setOf("Драма", "Без тегов"), titles(s.user, s.target))
    }

    @Test
    fun `ничего не переносить и без правила`() {
        val s = setup("sh_none")
        review(s.user, "Дюна", s.source)
        assertEquals(0, body(preview(s.user, s.token, rule("NONE")))["total"].asInt())
        assertEquals(200, status(joinWith(s.user, s.token, rule("NONE"))))
        assertEquals(emptySet(), titles(s.user, s.target))
    }

    @Test
    fun `уже опубликованное в новой группе не дублируется и чужие рекомендации не берутся`() {
        val s = setup("sh_dup")
        val other = newUser("sh_dup_o")
        join(s.user, s.source, other)
        review(other, "Чужая", s.source)
        review(s.user, "Моя", s.source)
        assertEquals(1, body(preview(s.user, s.token, rule("ALL")))["total"].asInt())
    }

    @Test
    fun `некорректное правило отклоняется а чужое приглашение нет`() {
        val s = setup("sh_bad")
        invalidRequests {
            assertEquals(400, status(preview(s.user, s.token, rule("CATEGORIES"))))
            assertEquals(400, status(preview(s.user, s.token, rule("TAGS"))))
            assertEquals(400, status(preview(s.user, s.token, rule("CATEGORIES", listOf("не-uuid")))))
            assertEquals("INVALID_INVITATION", code(preview(s.user, "AAAA-BBBB", rule("ALL"))))
        }
        // неизвестная категория не выбирает ничего
        val unknown = "00000000-0000-4000-8000-0000000000dd"
        assertEquals(0, body(preview(s.user, s.token, rule("CATEGORIES", listOf(unknown))))["total"].asInt())
    }
}
