package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode
import java.net.URLDecoder
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Секции ленты (И29), пересечение секции с фильтром панели, фасеты и сохранённые фильтры (И4, И30). */
class FeedTest : GroupsTestBase() {
    private fun addReview(
        access: String,
        title: String,
        category: String,
        groups: List<String>,
        rating: Int = 7,
        tags: List<String> = emptyList(),
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf(
                    "title" to title,
                    "rating" to rating,
                    "categoryId" to category(category),
                    "tags" to tags,
                    "groupIds" to groups,
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        clock.advance(Duration.ofSeconds(1))
        return body(r)["id"].asString()
    }

    /** GET с параметрами как есть (без повторного кодирования шаблоном адреса). */
    private fun fetch(
        access: String,
        path: String,
    ): MvcResult =
        mockMvc
            .perform(
                request(HttpMethod.GET, "/api/v1" + path.substringBefore('?')).apply {
                    header("Authorization", "Bearer $access")
                    path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach {
                        param(it.substringBefore('='), URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8))
                    }
                },
            ).andReturn()

    private fun ok(
        access: String,
        path: String,
    ): JsonNode {
        val r = fetch(access, path)
        check(status(r) == 200) { r.response.contentAsString }
        return body(r)
    }

    private fun titles(page: JsonNode) = page["items"].toList().map { it["title"].asString() }

    private fun sections(toc: JsonNode) = toc["sections"].toList().map { it["section"].asString() }

    private fun saveFilter(
        access: String,
        name: String,
        definition: Map<String, Any?>,
        inFeed: Boolean = true,
    ): JsonNode {
        val r =
            call(
                HttpMethod.POST,
                "/me/filters",
                access,
                mapOf(
                    "kind" to "FILTER",
                    "name" to name,
                    "definition" to mapOf("v" to 1) + definition,
                    "inFeed" to inFeed,
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)
    }

    private fun pin(
        access: String,
        key: String,
    ) = call(HttpMethod.POST, "/me/filters", access, mapOf("kind" to "CATEGORY", "categoryId" to category(key)))

    @Test
    fun `оглавление - закреплённые фильтры затем категории затем остальные по свежести`() {
        val u = newUser("feed_toc1")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Кофе", "food.coffee", listOf(g), rating = 9)
        addReview(u, "Книга", "books.sf", listOf(g), rating = 5)
        addReview(u, "Фильм", "film.movies", listOf(g), rating = 8)
        addReview(u, "Ещё книга", "books", listOf(g), rating = 10)
        addReview(u, "Личная без категории", "none", emptyList())

        // без закреплений: корни по самой свежей публикации
        assertEquals(
            listOf("category:${category("books")}", "category:${category("film")}", "category:${category("food")}"),
            sections(ok(u, "/feed")),
        )
        val books = ok(u, "/feed")["sections"][0]
        assertEquals(2, books["count"].asInt(), "подкатегории считаются в корне")
        assertEquals(false, books["pinned"].asBoolean())

        assertEquals(201, status(pin(u, "food")))
        val good = saveFilter(u, "Хорошее", mapOf("minRating" to 8))["id"].asString()
        saveFilter(u, "Не в ленте", mapOf("maxRating" to 3), inFeed = false)
        val toc = ok(u, "/feed")
        assertEquals(
            listOf(
                "filter:$good",
                "category:${category("food")}",
                "category:${category("books")}",
                "category:${category("film")}",
            ),
            sections(toc),
        )
        assertEquals(3, toc["sections"][0]["count"].asInt())
        assertEquals("Хорошее", toc["sections"][0]["name"].asString())
        assertTrue(toc["sections"][1]["pinned"].asBoolean())

        // фильтр панели накладывается на каждую секцию; пустые секции скрыты
        val narrowed = ok(u, "/feed?minRating=9")
        assertEquals(
            listOf("filter:$good", "category:${category("food")}", "category:${category("books")}"),
            sections(narrowed),
        )
        assertEquals(2, narrowed["sections"][0]["count"].asInt())

        // «Личное»: «Без категории» — последней
        val personal = personalGroup(u)
        addReview(u, "Личная книга", "books.sf", emptyList())
        assertEquals(
            listOf("category:${category("books")}", "category:${category("none")}"),
            sections(ok(u, "/feed?groupId=$personal")).filter {
                !it.startsWith("filter:") &&
                    it != "category:${category("food")}"
            },
        )
        assertEquals("category:${category("none")}", sections(ok(u, "/feed?groupId=$personal")).last())
    }

    @Test
    fun `записи секции - пересечение с панелью и свой курсор`() {
        val u = newUser("feed_sec2")
        val g = createGroup(u)["id"].asString()
        repeat(5) { addReview(u, "Кофе $it", "food.coffee", listOf(g), rating = 4 + it) }
        addReview(u, "Книга", "books.sf", listOf(g), rating = 9)
        val food = "category:${category("food")}"

        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = ok(u, "/groups/$g/reviews?section=$food&limit=2" + (cursor?.let { "&cursor=$it" } ?: ""))
            seen += titles(page)
            cursor = page.get("nextCursor")?.takeUnless { it.isNull }?.asString()
        } while (cursor != null)
        assertEquals((4 downTo 0).map { "Кофе $it" }, seen)

        assertEquals(listOf("Кофе 4", "Кофе 3"), titles(ok(u, "/reviews?section=$food&minRating=7")))
        val filter = saveFilter(u, "Высокие", mapOf("minRating" to 8, "q" to "книга"))["id"].asString()
        assertEquals(
            listOf("Книга", "Кофе 4"),
            titles(ok(u, "/reviews?section=filter:$filter")),
            "в секции поиска нет: q из фильтра не применяется",
        )
        assertEquals(listOf("Книга"), titles(ok(u, "/reviews?section=filter:$filter&categoryIds=${category("books")}")))

        invalidRequests {
            assertEquals(400, status(fetch(u, "/reviews?section=$food&q=кофе")))
            assertEquals(400, status(fetch(u, "/reviews?section=мусор")))
            assertEquals(400, status(fetch(u, "/reviews?minRating=9&maxRating=3")))
        }
        val other = newUser("feed_sec2o")
        assertEquals(404, status(fetch(other, "/reviews?section=filter:$filter")), "чужой фильтр не раскрывается")
    }

    @Test
    fun `фасеты и теги моих групп`() {
        val u = newUser("feed_fac3")
        val friend = newUser("feed_fac3f")
        val g = createGroup(u)["id"].asString()
        join(u, g, friend)
        addReview(u, "Кофе", "food.coffee", listOf(g), tags = listOf("утро", "кофе"))
        addReview(friend, "Ещё кофе", "food.coffee", listOf(g), tags = listOf("кофе"))
        addReview(u, "Книга", "books.sf", listOf(g), rating = 3)
        addReview(u, "Личное", "books", emptyList(), tags = listOf("секрет"))

        val facets = ok(u, "/groups/$g/facets")
        val counts = facets["categories"].toList().associate { it["categoryId"].asString() to it["count"].asInt() }
        assertEquals(2, counts[category("food")])
        assertEquals(2, counts[category("food.coffee")])
        assertEquals(1, counts[category("books.sf")])
        assertEquals("кофе", facets["tags"][0]["name"].asString())
        assertEquals(2, facets["tags"][0]["count"].asInt())
        assertEquals(
            setOf("feed_fac3", "feed_fac3f"),
            facets["authors"]
                .toList()
                .map {
                    it["author"]["username"].asString()
                }.toSet(),
        )
        assertEquals(1, ok(u, "/reviews/facets?minRating=5&authorIds=${userId(friend)}")["authors"].size())
        assertEquals(0, ok(u, "/groups/${personalGroup(u)}/facets")["authors"].size(), "в «Личном» авторов нет")

        val mine = ok(u, "/me/tags").toList().map { it["name"].asString() }
        assertTrue("секрет" in mine && "кофе" in mine)
        assertTrue("секрет" !in ok(friend, "/me/tags").toList().map { it["name"].asString() })
        assertEquals(listOf("утро"), ok(u, "/me/tags?prefix=ут").toList().map { it["name"].asString() })
        assertEquals(3, ok(u, "/groups/$g")["reviewCount"].asInt())
        assertEquals(3, body(get("/groups", u))[0]["reviewCount"].asInt())
    }

    @Test
    fun `сохранённые фильтры - порядок правка лимиты и закрепление`() {
        val u = newUser("feed_sf4")
        val b = saveFilter(u, "Бета", mapOf("tags" to listOf("кофе")), inFeed = false)["id"].asString()
        val a = saveFilter(u, "альфа", mapOf("tagMode" to "ANY"), inFeed = false)["id"].asString()
        val pinned = saveFilter(u, "Закреплённый", emptyMap())["id"].asString()
        val food = body(pin(u, "food"))["id"].asString()
        val books = body(pin(u, "books"))["id"].asString()

        val list = body(get("/me/filters", u)).toList()
        assertEquals(
            listOf(pinned, food, books, a, b),
            list.map { it["id"].asString() },
            "закреплённые, затем по алфавиту",
        )
        assertEquals(listOf(0, 0, 1), list.take(3).map { it["position"].asInt() })

        assertEquals("ALREADY_PINNED", code(pin(u, "food")))
        assertEquals(400, status(pin(u, "food.coffee")), "закрепить можно только корень")
        val bad =
            call(
                HttpMethod.POST,
                "/me/filters",
                u,
                mapOf(
                    "kind" to "FILTER",
                    "name" to " ",
                    "definition" to mapOf("v" to 1),
                ),
            )
        assertEquals(400, status(bad))
        invalidRequests {
            val wrongRange =
                call(
                    HttpMethod.POST,
                    "/me/filters",
                    u,
                    mapOf(
                        "kind" to "FILTER",
                        "name" to "x",
                        "definition" to mapOf("v" to 1, "minRating" to 9, "maxRating" to 2),
                    ),
                )
            assertEquals(400, status(wrongRange))
        }

        val reordered =
            call(
                HttpMethod.PUT,
                "/me/filters/order",
                u,
                mapOf(
                    "filterIds" to listOf(pinned),
                    "categoryIds" to listOf(books, food),
                ),
            )
        assertEquals(200, status(reordered))
        assertEquals(listOf(pinned, books, food), body(reordered).toList().take(3).map { it["id"].asString() })
        assertEquals(
            400,
            status(
                call(
                    HttpMethod.PUT,
                    "/me/filters/order",
                    u,
                    mapOf(
                        "filterIds" to emptyList<String>(),
                        "categoryIds" to listOf(books),
                    ),
                ),
            ),
        )

        val renamed = call(HttpMethod.PATCH, "/me/filters/$b", u, mapOf("name" to "Гамма", "inFeed" to true))
        assertEquals("Гамма", body(renamed)["name"].asString())
        assertEquals(1, body(renamed)["position"].asInt(), "встал последним среди закреплённых фильтров")
        assertEquals(400, status(call(HttpMethod.PATCH, "/me/filters/$food", u, mapOf("name" to "Нельзя"))))
        assertEquals(204, status(delete("/me/filters/$food", u)))
        assertEquals(404, status(delete("/me/filters/$food", u)))
        val other = newUser("feed_sf4o")
        assertEquals(404, status(call(HttpMethod.PATCH, "/me/filters/$a", other, mapOf("name" to "Чужой"))))

        // всего записей: 4 + 46 = 50 — предел
        repeat(46) { saveFilter(u, "Фильтр $it", emptyMap(), inFeed = false) }
        val overLimit =
            call(
                HttpMethod.POST,
                "/me/filters",
                u,
                mapOf(
                    "kind" to "FILTER",
                    "name" to "Лишний",
                    "definition" to mapOf("v" to 1),
                ),
            )
        assertEquals("LIMIT_EXCEEDED", code(overLimit))
    }
}
