package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Перенос рекомендаций при вступлении в группу: правила, автоподбор категории, ручные назначения. */
class SharingTest : GroupsTestBase() {
    private fun categories(
        access: String,
        groupId: String,
    ): JsonNode = body(get("/groups/$groupId/categories", access))

    /** Категория с подкатегориями и две её дочерние (из шаблона). */
    private fun parentWithChildren(
        access: String,
        groupId: String,
    ): Pair<String, List<String>> {
        val all = categories(access, groupId).toList()
        val parent = all.first { p -> all.any { it["parentId"]?.asString() == p["id"].asString() } }["id"].asString()
        return parent to all.filter { it["parentId"]?.asString() == parent }.map { it["id"].asString() }
    }

    private fun cat(
        access: String,
        groupId: String,
        index: Int,
    ) = categories(access, groupId)[index]["id"].asString()

    private fun review(
        access: String,
        title: String,
        groupId: String? = null,
        categoryId: String? = null,
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
                    "tags" to tags,
                    "publications" to
                        if (groupId !=
                            null
                        ) {
                            listOf(mapOf("groupId" to groupId, "categoryId" to categoryId))
                        } else {
                            emptyList()
                        },
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun rule(
        mode: String,
        categoryIds: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        withSubcategories: Boolean = true,
    ) = mapOf("mode" to mode, "categoryIds" to categoryIds, "tags" to tags, "withSubcategories" to withSubcategories)

    private fun preview(
        access: String,
        token: String,
        rule: Map<String, Any>,
    ) = call(HttpMethod.POST, "/invitations/share-preview", access, mapOf("token" to token, "share" to rule))

    private fun joinWith(
        access: String,
        token: String,
        rule: Map<String, Any>?,
        assignments: List<Map<String, String>> = emptyList(),
    ) = call(
        HttpMethod.POST,
        "/invitations/accept",
        access,
        mapOf("token" to token, "share" to rule, "assignments" to assignments).filterValues { it != null },
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
    fun `все рекомендации переносятся а личные требуют назначения`() {
        val s = setup("sh_all")
        val book = cat(s.user, s.source, 0)
        review(s.user, "Дюна", s.source, book)
        val personal = review(s.user, "Личная заметка")

        val p = body(preview(s.user, s.token, rule("ALL")))
        assertEquals(2, p["total"].asInt())
        assertEquals(1, p["matched"].asInt())
        assertEquals(listOf(personal), p["unmatched"].toList().map { it["reviewId"].asString() })
        assertTrue(p["categories"].size() > 0)
        assertEquals(emptySet(), titles(s.admin, s.target), "предпросмотр ничего не меняет")

        val target = cat(s.admin, s.target, 0)
        val joined =
            joinWith(s.user, s.token, rule("ALL"), listOf(mapOf("reviewId" to personal, "categoryId" to target)))
        assertEquals(200, status(joined))
        assertEquals(setOf("Дюна", "Личная заметка"), titles(s.admin, s.target))
    }

    @Test
    fun `без назначения несоответствующая запись не переносится`() {
        val s = setup("sh_skip")
        review(s.user, "Дюна", s.source, cat(s.user, s.source, 0))
        review(s.user, "Личная заметка")
        assertEquals(200, status(joinWith(s.user, s.token, rule("ALL"))))
        assertEquals(setOf("Дюна"), titles(s.user, s.target))
    }

    @Test
    fun `назначение в чужую категорию игнорируется`() {
        val s = setup("sh_foreign")
        val personal = review(s.user, "Личная заметка")
        val inSource = cat(s.user, s.source, 0)
        val joined =
            joinWith(
                s.user,
                s.token,
                rule("ALL"),
                listOf(
                    mapOf(
                        "reviewId" to personal,
                        "categoryId" to inSource,
                    ),
                ),
            )
        assertEquals(200, status(joined))
        assertEquals(emptySet(), titles(s.user, s.target))
    }

    @Test
    fun `категория категории с подкатегориями и без`() {
        val s = setup("sh_cat")
        val (parent, children) = parentWithChildren(s.user, s.source)
        review(s.user, "В родителе", s.source, parent)
        review(s.user, "В потомке", s.source, children.first())
        review(
            s.user,
            "В другой",
            s.source,
            categories(s.user, s.source)
                .first {
                    it["id"].asString() != parent &&
                        it["id"].asString() !in children
                }["id"]
                .asString(),
        )

        val withSub = body(preview(s.user, s.token, rule("CATEGORIES", listOf(parent))))
        assertEquals(2, withSub["total"].asInt())
        val noSub = body(preview(s.user, s.token, rule("CATEGORIES", listOf(parent), withSubcategories = false)))
        assertEquals(1, noSub["total"].asInt())
        val except = body(preview(s.user, s.token, rule("CATEGORIES_EXCEPT", listOf(parent))))
        assertEquals(1, except["total"].asInt())

        assertEquals(200, status(joinWith(s.user, s.token, rule("CATEGORIES", listOf(parent)))))
        assertEquals(setOf("В родителе", "В потомке"), titles(s.user, s.target))
    }

    @Test
    fun `теги включают и исключают`() {
        val s = setup("sh_tag")
        val c = cat(s.user, s.source, 0)
        review(s.user, "Фантастика", s.source, c, tags = listOf("scifi"))
        review(s.user, "Драма", s.source, c, tags = listOf("drama"))
        review(s.user, "Без тегов", s.source, c)

        assertEquals(1, body(preview(s.user, s.token, rule("TAGS", tags = listOf("#SciFi"))))["total"].asInt())
        assertEquals(2, body(preview(s.user, s.token, rule("TAGS", tags = listOf("scifi", "drama"))))["total"].asInt())
        assertEquals(2, body(preview(s.user, s.token, rule("TAGS_EXCEPT", tags = listOf("scifi"))))["total"].asInt())

        assertEquals(200, status(joinWith(s.user, s.token, rule("TAGS_EXCEPT", tags = listOf("scifi")))))
        assertEquals(setOf("Драма", "Без тегов"), titles(s.user, s.target))
    }

    @Test
    fun `ничего не переносить и без правила`() {
        val s = setup("sh_none")
        review(s.user, "Дюна", s.source, cat(s.user, s.source, 0))
        assertEquals(0, body(preview(s.user, s.token, rule("NONE")))["total"].asInt())
        assertEquals(200, status(joinWith(s.user, s.token, rule("NONE"))))
        assertEquals(emptySet(), titles(s.user, s.target))
    }

    @Test
    fun `уже опубликованное в новой группе не дублируется и чужие рекомендации не берутся`() {
        val s = setup("sh_dup")
        val other = newUser("sh_dup_o")
        join(s.user, s.source, other)
        review(other, "Чужая", s.source, cat(other, s.source, 0))
        review(s.user, "Моя", s.source, cat(s.user, s.source, 0))
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
        // правило с категорией чужой группы не выбирает ничего
        val foreign = cat(s.admin, s.target, 0)
        assertEquals(0, body(preview(s.user, s.token, rule("CATEGORIES", listOf(foreign))))["total"].asInt())
    }
}
