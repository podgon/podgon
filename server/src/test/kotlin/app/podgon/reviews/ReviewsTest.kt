package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import app.podgon.reviews.domain.TagCleanup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import tools.jackson.databind.JsonNode
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewsTest : GroupsTestBase() {
    private fun review(
        access: String,
        title: String = "Дюна",
        rating: Int = 9,
        tags: List<String> = emptyList(),
        groupIds: List<String> = emptyList(),
        description: String = "Отличная книга",
        categoryId: String = category("books.sf"),
    ) = call(
        HttpMethod.POST,
        "/reviews",
        access,
        mapOf(
            "title" to title,
            "description" to description,
            "rating" to rating,
            "categoryId" to categoryId,
            "tags" to tags,
            "groupIds" to groupIds,
        ),
    )

    private fun created(
        access: String,
        title: String = "Дюна",
        groupIds: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        categoryId: String = category("books.sf"),
    ): JsonNode {
        val r = review(access, title, groupIds = groupIds, tags = tags, categoryId = categoryId)
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)
    }

    /** Пустые поля в ответе не передаются, поэтому «нет курсора» — отсутствие поля. */
    private fun nextCursor(page: JsonNode): String? = page.get("nextCursor")?.takeUnless { it.isNull }?.asString()

    private fun setGroups(
        access: String,
        reviewId: String,
        groupIds: List<String>,
    ) = call(HttpMethod.PUT, "/reviews/$reviewId/groups", access, mapOf("groupIds" to groupIds))

    private fun feedTitles(
        access: String,
        groupId: String,
    ): List<String> = body(get("/groups/$groupId/reviews", access))["items"].toList().map { it["title"].asString() }

    private fun fields(result: org.springframework.test.web.servlet.MvcResult): Map<String, String> =
        body(result)["errors"].toList().associate { it["field"].asString() to it["reason"].asString() }

    @Autowired
    lateinit var tagCleanup: TagCleanup

    @Test
    fun `ночная чистка удаляет только неиспользуемые теги`() {
        val author = newUser("rv_tags_gc")
        val id = created(author, tags = listOf("остаётся", "уходит"))["id"].asString()
        created(author, tags = listOf("общий"))
        assertEquals(
            200,
            status(
                call(
                    HttpMethod.PATCH,
                    "/reviews/$id",
                    author,
                    mapOf(
                        "tags" to listOf("остаётся", "общий"),
                    ),
                ),
            ),
        )
        tagCleanup.run()
        val names =
            jdbc
                .sql("select name from tags")
                .query(String::class.java)
                .list()
                .toSet()
        assertEquals(setOf("остается", "общий"), names, "«ё» в тегах хранится как «е»")
    }

    @Test
    fun `личная рекомендация видна только автору и лежит в Личном`() {
        val author = newUser("rv_author1")
        val other = newUser("rv_other1")
        val r = created(author, tags = listOf("#SciFi", " классика ", "scifi"))
        val id = r["id"].asString()
        assertEquals(setOf("классика", "scifi"), r["tags"].toList().map { it.asString() }.toSet())
        assertEquals(0, r["publications"].size())
        assertTrue(r["personal"].asBoolean())
        assertEquals(category("books.sf"), r["categoryId"].asString())
        assertEquals("rv_author1", r["author"]["username"].asString())

        assertEquals(200, status(get("/reviews/$id", author)))
        assertEquals(404, status(get("/reviews/$id", other)))
        val personal = personalGroup(author)
        assertEquals(listOf("Дюна"), feedTitles(author, personal))
        assertEquals(404, status(get("/groups/$personal/reviews", other)), "чужое «Личное» не раскрывается")
        assertEquals(0, body(get("/me/reviews", author))["items"].size(), "в «Мои» только групповые")
    }

    @Test
    fun `публикация в группу видна участникам и только в их группах`() {
        val author = newUser("rv_author2")
        val member = newUser("rv_member2")
        val outsider = newUser("rv_out2")
        val g1 = createGroup(author, "Первая")["id"].asString()
        val g2 = createGroup(author, "Вторая")["id"].asString()
        join(author, g1, member)
        val id = created(author, groupIds = listOf(g1, g2))["id"].asString()

        val seenByMember = body(get("/reviews/$id", member))
        assertEquals(listOf(g1), seenByMember["publications"].toList().map { it["groupId"].asString() })
        assertFalse(seenByMember["personal"].asBoolean())
        assertEquals(0, feedTitles(author, personalGroup(author)).size, "групповая не лежит в «Личном»")
        assertEquals(2, body(get("/reviews/$id", author))["publications"].size())
        assertEquals(404, status(get("/reviews/$id", outsider)))
        assertEquals(1, body(get("/groups/$g1/reviews", member))["items"].size())
        assertEquals(404, status(get("/groups/$g1/reviews", outsider)))
    }

    @Test
    fun `публиковать можно только в свои обычные группы`() {
        val author = newUser("rv_author3")
        val stranger = newUser("rv_stranger3")
        val mine = createGroup(author, "Моя")["id"].asString()
        val foreign = createGroup(stranger, "Чужая")["id"].asString()

        assertEquals(404, status(review(author, groupIds = listOf(foreign))))
        assertEquals(404, status(review(author, groupIds = listOf(personalGroup(stranger)))))
        for (system in listOf(personalGroup(author), favoritesGroup(author))) {
            val refused = review(author, groupIds = listOf(system))
            assertEquals(400, status(refused))
            assertEquals(mapOf("groupIds" to "INVALID_FORMAT"), fields(refused))
        }
        assertEquals(400, status(review(author, groupIds = listOf(mine, mine))))
        assertEquals(
            0,
            body(get("/me/reviews", author))["items"].size() + feedTitles(author, personalGroup(author)).size,
            "неудачное создание не оставляет рекомендацию",
        )
        assertEquals(201, status(review(author, groupIds = listOf(mine))))
    }

    @Test
    fun `категория обязательна а Без категории только у личной`() {
        val author = newUser("rv_author3c")
        val g = createGroup(author)["id"].asString()
        val none = category("none")

        val inGroup = review(author, groupIds = listOf(g), categoryId = none)
        assertEquals(400, status(inGroup))
        assertEquals(mapOf("categoryId" to "REQUIRED"), fields(inGroup))
        val unknown = review(author, categoryId = "00000000-0000-4000-8000-0000000000cc")
        assertEquals(mapOf("categoryId" to "INVALID_FORMAT"), fields(unknown))
        invalidRequests {
            val missing =
                call(HttpMethod.POST, "/reviews", author, mapOf("title" to "Без категории", "rating" to 5))
            assertEquals(400, status(missing))
        }

        val personal = created(author, "Личная", categoryId = none)
        val id = personal["id"].asString()
        assertEquals(none, personal["categoryId"].asString())
        val opened = setGroups(author, id, listOf(g))
        assertEquals(400, status(opened), "открыть замок без категории нельзя")
        assertEquals(mapOf("categoryId" to "REQUIRED"), fields(opened))

        val group = created(author, "Групповая", groupIds = listOf(g))["id"].asString()
        val toNone = call(HttpMethod.PATCH, "/reviews/$group", author, mapOf("categoryId" to none))
        assertEquals(mapOf("categoryId" to "REQUIRED"), fields(toNone))
        val toFilm = call(HttpMethod.PATCH, "/reviews/$id", author, mapOf("categoryId" to category("film.movies")))
        assertEquals(category("film.movies"), body(toFilm)["categoryId"].asString())
        assertEquals(200, status(setGroups(author, id, listOf(g))))
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
        val id = created(author, groupIds = listOf(g))["id"].asString()

        val patch = mapOf("title" to "Новое", "rating" to 5)
        assertEquals(403, status(call(HttpMethod.PATCH, "/reviews/$id", member, patch)))
        assertEquals(404, status(call(HttpMethod.PATCH, "/reviews/$id", outsider, patch)))
        assertEquals(403, status(setGroups(member, id, emptyList())))
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
        val personal = personalGroup(author)
        val id = created(author, groupIds = listOf(g1))["id"].asString()
        val firstSharedAt = body(get("/reviews/$id", author))["publications"][0]["sharedAt"].asString()

        clock.advance(Duration.ofMinutes(5))
        val both = body(setGroups(author, id, listOf(g1, g2)))
        val inG1 = both["publications"].first { it["groupId"].asString() == g1 }
        assertEquals(firstSharedAt, inG1["sharedAt"].asString())
        assertNotEquals(
            firstSharedAt,
            both["publications"].first { it["groupId"].asString() == g2 }["sharedAt"].asString(),
        )

        val onlyG2 = body(setGroups(author, id, listOf(g2)))
        assertEquals(1, onlyG2["publications"].size())
        assertEquals(0, body(get("/groups/$g1/reviews", author))["items"].size())

        val closed = body(setGroups(author, id, emptyList()))
        assertEquals(0, closed["publications"].size())
        assertTrue(closed["personal"].asBoolean(), "пустой набор делает рекомендацию личной")
        assertEquals(listOf("Дюна"), feedTitles(author, personal))

        val reopened = body(setGroups(author, id, listOf(g1)))
        assertFalse(reopened["personal"].asBoolean())
        assertEquals(0, feedTitles(author, personal).size, "открытая рекомендация уходит из «Личного»")
        assertEquals(400, status(setGroups(author, id, listOf(g1, personal))))
    }

    @Test
    fun `лента идёт страницами без повторов и пропусков`() {
        val author = newUser("rv_author7")
        val g = createGroup(author)["id"].asString()
        val cat = category("books.sf")
        val titles =
            (1..5).map { i ->
                clock.advance(Duration.ofMinutes(1))
                "Рек $i".also { created(author, it, listOf(g)) }
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
        repeat(5) { created(author, "Одновременно $it", listOf(g)) }
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
    fun `мои рекомендации только групповые и идут страницами`() {
        val author = newUser("rv_author9")
        val g = createGroup(author)["id"].asString()
        created(author, "Личная")
        repeat(3) {
            clock.advance(Duration.ofMinutes(1))
            created(author, "Моя $it", listOf(g))
        }
        val first = body(get("/me/reviews?limit=2", author))
        assertEquals(listOf("Моя 2", "Моя 1"), first["items"].toList().map { it["title"].asString() })
        assertEquals(category("books.sf"), first["items"][0]["categoryId"].asString())
        val second = body(get("/me/reviews?limit=2&cursor=${nextCursor(first)}", author))
        assertEquals(listOf("Моя 0"), second["items"].toList().map { it["title"].asString() })
        assertNull(nextCursor(second))
        invalidRequests {
            assertEquals(400, status(get("/me/reviews?cursor=мусор", author)))
        }
    }

    @Test
    fun `удаление группы переносит рекомендации в Личное`() {
        val author = newUser("rv_author12")
        val g = createGroup(author)["id"].asString()
        val other = createGroup(author, "Другая")["id"].asString()
        val only = created(author, "Только здесь", listOf(g))["id"].asString()
        created(author, "И там", listOf(g, other))
        assertEquals(204, status(delete("/groups/$g?confirm=true", author)))
        val after = body(get("/reviews/$only", author))
        assertEquals(0, after["publications"].size())
        assertTrue(after["personal"].asBoolean())
        assertEquals(listOf("Только здесь"), feedTitles(author, personalGroup(author)))
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
