package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import tools.jackson.databind.JsonNode
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewsTest : GroupsTestBase() {
    private fun categoryId(
        access: String,
        groupId: String,
        index: Int = 0,
    ): String = body(get("/groups/$groupId/categories", access))[index]["id"].asString()

    private fun pub(
        groupId: String,
        categoryId: String,
    ) = mapOf("groupId" to groupId, "categoryId" to categoryId)

    private fun review(
        access: String,
        title: String = "Дюна",
        rating: Int = 9,
        tags: List<String> = emptyList(),
        publications: List<Map<String, String>> = emptyList(),
        description: String = "Отличная книга",
    ) = call(
        HttpMethod.POST,
        "/reviews",
        access,
        mapOf(
            "title" to title,
            "description" to description,
            "rating" to rating,
            "tags" to tags,
            "publications" to publications,
        ),
    )

    private fun created(
        access: String,
        title: String = "Дюна",
        publications: List<Map<String, String>> = emptyList(),
        tags: List<String> = emptyList(),
    ): JsonNode {
        val r = review(access, title, publications = publications, tags = tags)
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)
    }

    /** Пустые поля в ответе не передаются, поэтому «нет курсора» — отсутствие поля. */
    private fun nextCursor(page: JsonNode): String? = page.get("nextCursor")?.takeUnless { it.isNull }?.asString()

    private fun setPubs(
        access: String,
        reviewId: String,
        publications: List<Map<String, String>>,
    ) = call(HttpMethod.PUT, "/reviews/$reviewId/groups", access, mapOf("publications" to publications))

    @Test
    fun `личная рекомендация видна только автору`() {
        val author = newUser("rv_author1")
        val other = newUser("rv_other1")
        val r = created(author, tags = listOf("#SciFi", " классика ", "scifi"))
        val id = r["id"].asString()
        assertEquals(setOf("классика", "scifi"), r["tags"].toList().map { it.asString() }.toSet())
        assertEquals(0, r["publications"].size())
        assertEquals("rv_author1", r["author"]["username"].asString())

        assertEquals(200, status(get("/reviews/$id", author)))
        assertEquals(404, status(get("/reviews/$id", other)))
        assertEquals(1, body(get("/me/reviews", author))["items"].size())
        assertEquals(0, body(get("/me/reviews", other))["items"].size())
    }

    @Test
    fun `публикация в группу видна участникам и только в их группах`() {
        val author = newUser("rv_author2")
        val member = newUser("rv_member2")
        val outsider = newUser("rv_out2")
        val g1 = createGroup(author, "Первая")["id"].asString()
        val g2 = createGroup(author, "Вторая")["id"].asString()
        join(author, g1, member)
        val id =
            created(
                author,
                publications = listOf(pub(g1, categoryId(author, g1)), pub(g2, categoryId(author, g2))),
            )["id"].asString()

        val seenByMember = body(get("/reviews/$id", member))
        assertEquals(listOf(g1), seenByMember["publications"].toList().map { it["groupId"].asString() })
        assertEquals(2, body(get("/reviews/$id", author))["publications"].size())
        assertEquals(404, status(get("/reviews/$id", outsider)))
        assertEquals(1, body(get("/groups/$g1/reviews", member))["items"].size())
        assertEquals(404, status(get("/groups/$g1/reviews", outsider)))
    }

    @Test
    fun `публиковать можно только в свои группы и в категории этой группы`() {
        val author = newUser("rv_author3")
        val stranger = newUser("rv_stranger3")
        val mine = createGroup(author, "Моя")["id"].asString()
        val foreign = createGroup(stranger, "Чужая")["id"].asString()
        val minCat = categoryId(author, mine)
        val foreignCat = categoryId(stranger, foreign)

        assertEquals(404, status(review(author, publications = listOf(pub(foreign, foreignCat)))))
        val wrongCategory = review(author, publications = listOf(pub(mine, foreignCat)))
        assertEquals(400, status(wrongCategory))
        assertEquals("VALIDATION_FAILED", code(wrongCategory))
        assertEquals(
            0,
            body(get("/me/reviews", author))["items"].size(),
            "неудачное создание не оставляет рекомендацию",
        )
        assertEquals(201, status(review(author, publications = listOf(pub(mine, minCat)))))
        invalidRequests {
            assertEquals(400, status(review(author, publications = listOf(pub(mine, minCat), pub(mine, minCat)))))
        }
    }

    @Test
    fun `валидация названия оценки и тегов`() {
        val author = newUser("rv_author4")
        invalidRequests {
            val bad = review(author, title = "  ", rating = 11, tags = listOf("плохой тег"))
            assertEquals(400, status(bad))
            assertEquals(
                setOf("title", "rating", "tags"),
                body(bad)["errors"].toList().map { it["field"].asString() }.toSet(),
            )
            assertEquals(400, status(review(author, rating = 0)))
            assertEquals(400, status(review(author, tags = (1..11).map { "t$it" })))
            assertEquals(400, status(review(author, description = "я".repeat(5001))))
        }
        assertEquals(201, status(review(author, rating = 10, tags = (1..10).map { "t$it" })))
        assertEquals(201, status(review(author, rating = 1, tags = listOf("ёлка_2-й"))))
    }

    @Test
    fun `менять и удалять может только автор`() {
        val author = newUser("rv_author5")
        val member = newUser("rv_member5")
        val outsider = newUser("rv_out5")
        val g = createGroup(author)["id"].asString()
        join(author, g, member)
        val id = created(author, publications = listOf(pub(g, categoryId(author, g))))["id"].asString()

        val patch = mapOf("title" to "Новое", "rating" to 5)
        assertEquals(403, status(call(HttpMethod.PATCH, "/reviews/$id", member, patch)))
        assertEquals(404, status(call(HttpMethod.PATCH, "/reviews/$id", outsider, patch)))
        assertEquals(403, status(setPubs(member, id, emptyList())))
        assertEquals(403, status(delete("/reviews/$id", member)))
        assertEquals(404, status(delete("/reviews/$id", outsider)))

        val updated =
            call(
                HttpMethod.PATCH,
                "/reviews/$id",
                author,
                mapOf(
                    "title" to "Новое",
                    "rating" to 5,
                    "tags" to listOf("a"),
                ),
            )
        assertEquals(200, status(updated))
        assertEquals("Новое", body(updated)["title"].asString())
        assertEquals(5, body(updated)["rating"].asInt())
        assertEquals("Отличная книга", body(updated)["description"].asString())
        assertEquals(listOf("a"), body(updated)["tags"].toList().map { it.asString() })
        assertEquals(204, status(delete("/reviews/$id", author)))
        assertEquals(404, status(get("/reviews/$id", author)))
        assertEquals(0, body(get("/groups/$g/reviews", member))["items"].size())
    }

    @Test
    fun `набор публикаций заменяется целиком а время публикации сохраняется`() {
        val author = newUser("rv_author6")
        val g1 = createGroup(author, "Первая")["id"].asString()
        val g2 = createGroup(author, "Вторая")["id"].asString()
        val c1 = categoryId(author, g1, 0)
        val c1b = categoryId(author, g1, 1)
        val c2 = categoryId(author, g2)
        val id = created(author, publications = listOf(pub(g1, c1)))["id"].asString()
        val firstSharedAt = body(get("/reviews/$id", author))["publications"][0]["sharedAt"].asString()

        clock.advance(Duration.ofMinutes(5))
        val both = body(setPubs(author, id, listOf(pub(g1, c1b), pub(g2, c2))))
        val inG1 = both["publications"].first { it["groupId"].asString() == g1 }
        assertEquals(c1b, inG1["categoryId"].asString())
        assertEquals(firstSharedAt, inG1["sharedAt"].asString())
        assertNotEquals(
            firstSharedAt,
            both["publications"].first { it["groupId"].asString() == g2 }["sharedAt"].asString(),
        )

        val onlyG2 = body(setPubs(author, id, listOf(pub(g2, c2))))
        assertEquals(1, onlyG2["publications"].size())
        assertEquals(0, body(get("/groups/$g1/reviews", author))["items"].size())
        assertEquals(0, body(setPubs(author, id, emptyList()))["publications"].size())
        assertEquals(200, status(get("/reviews/$id", author)), "рекомендация осталась личной")
    }

    @Test
    fun `лента идёт страницами без повторов и пропусков`() {
        val author = newUser("rv_author7")
        val g = createGroup(author)["id"].asString()
        val cat = categoryId(author, g)
        val titles =
            (1..5).map { i ->
                clock.advance(Duration.ofMinutes(1))
                "Рек $i".also { created(author, it, listOf(pub(g, cat))) }
            }

        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val query = "/groups/$g/reviews?limit=2" + (cursor?.let { "&cursor=$it" } ?: "")
            val page = body(get(query, author))
            seen += page["items"].toList().map { it["title"].asString() }
            assertTrue(page["items"].all { it["categoryId"].asString() == cat && it.get("sharedAt") != null })
            cursor = nextCursor(page)
            pages++
        } while (cursor != null)
        assertEquals(titles.reversed(), seen)
        assertEquals(3, pages)
    }

    @Test
    fun `лента с одинаковым временем публикации не теряет записи`() {
        val author = newUser("rv_author8")
        val g = createGroup(author)["id"].asString()
        val cat = categoryId(author, g)
        repeat(5) { created(author, "Одновременно $it", listOf(pub(g, cat))) }
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = body(get("/groups/$g/reviews?limit=2" + (cursor?.let { "&cursor=$it" } ?: ""), author))
            ids += page["items"].toList().map { it["id"].asString() }
            cursor = nextCursor(page)
        } while (cursor != null)
        assertEquals(5, ids.distinct().size)
    }

    @Test
    fun `мои рекомендации и параметры страницы`() {
        val author = newUser("rv_author9")
        repeat(3) {
            clock.advance(Duration.ofMinutes(1))
            created(author, "Моя $it")
        }
        val first = body(get("/me/reviews?limit=2", author))
        assertEquals(listOf("Моя 2", "Моя 1"), first["items"].toList().map { it["title"].asString() })
        assertNull(first["items"][0].get("categoryId"))
        val second = body(get("/me/reviews?limit=2&cursor=${nextCursor(first)}", author))
        assertEquals(listOf("Моя 0"), second["items"].toList().map { it["title"].asString() })
        assertNull(nextCursor(second))
        invalidRequests {
            assertEquals(400, status(get("/me/reviews?cursor=мусор", author)))
        }
    }

    @Test
    fun `удаление категории учитывает публикации и защищает чужие`() {
        val admin = newUser("rv_admin10")
        val member = newUser("rv_member10")
        val g = createGroup(admin)["id"].asString()
        join(admin, g, member)
        val cat = categoryId(admin, g)
        created(admin, "Моя", listOf(pub(g, cat)))

        val own = body(get("/groups/$g/categories/$cat/delete-impact", admin))
        assertEquals(1, own["publications"].asInt())
        assertEquals(0, own["foreignPublications"].asInt())

        // чужая публикация в той же категории: админ её удалить не может
        val foreign = created(member, "Чужая", listOf(pub(g, cat)))["id"].asString()
        val impact = body(get("/groups/$g/categories/$cat/delete-impact", admin))
        assertEquals(2, impact["publications"].asInt())
        assertEquals(1, impact["foreignPublications"].asInt())
        val refused = delete("/groups/$g/categories/$cat?confirm=true", admin)
        assertEquals(409, status(refused))
        assertEquals("CATEGORY_HAS_FOREIGN_REVIEWS", code(refused))

        // автор снял публикацию — удалять можно, остаются только публикации админа
        assertEquals(200, status(setPubs(member, foreign, emptyList())))
        assertEquals(400, status(delete("/groups/$g/categories/$cat", admin)))
        assertEquals(204, status(delete("/groups/$g/categories/$cat?confirm=true", admin)))
        assertEquals(0, body(get("/groups/$g/reviews", admin))["items"].size())
        assertEquals(
            2,
            body(get("/me/reviews", admin))["items"].size() + body(get("/me/reviews", member))["items"].size(),
        )
    }

    @Test
    fun `публикации в подкатегориях тоже считаются при удалении родителя`() {
        val admin = newUser("rv_admin11")
        val g = createGroup(admin)["id"].asString()
        val parent = categoryId(admin, g)
        val child =
            body(
                call(
                    HttpMethod.POST,
                    "/groups/$g/categories",
                    admin,
                    mapOf("name" to "Дочерняя", "parentId" to parent),
                ),
            )["id"].asString()
        created(admin, "В дочерней", listOf(pub(g, child)))
        val impact = body(get("/groups/$g/categories/$parent/delete-impact", admin))
        assertEquals(1, impact["subcategories"].asInt())
        assertEquals(1, impact["publications"].asInt())
        assertEquals(204, status(delete("/groups/$g/categories/$parent?confirm=true", admin)))
        assertEquals(0, body(get("/groups/$g/reviews", admin))["items"].size())
    }

    @Test
    fun `удаление группы оставляет рекомендации личными`() {
        val author = newUser("rv_author12")
        val g = createGroup(author)["id"].asString()
        val id = created(author, publications = listOf(pub(g, categoryId(author, g))))["id"].asString()
        assertEquals(204, status(delete("/groups/$g?confirm=true", author)))
        val after = body(get("/reviews/$id", author))
        assertEquals(0, after["publications"].size())
    }

    @Test
    fun `чужой идентификатор и неверный адрес дают 404`() {
        val author = newUser("rv_author13")
        assertEquals(404, status(get("/reviews/00000000-0000-4000-8000-0000000000aa", author)))
        invalidRequests {
            assertEquals(404, status(get("/reviews/not-a-uuid", author)))
        }
    }

    @Test
    fun `без токена рекомендации недоступны`() {
        invalidRequests {
            assertEquals(401, status(get("/me/reviews", null)))
        }
    }
}
