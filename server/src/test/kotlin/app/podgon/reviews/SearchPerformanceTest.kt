package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Нагрузочный smoke поиска: 100 000 рекомендаций в двух группах, p95 каждого типового запроса < 300 мс.
 * На общих раннерах CI (медленные и шумные) порог шире — 1500 мс: тест там ловит поломку плана запроса,
 * а не оценивает скорость железа.
 */
class SearchPerformanceTest : GroupsTestBase() {
    private val limitMs = if (System.getenv("CI") != null) 1500 else 300

    private fun timed(
        access: String,
        path: String,
        params: Map<String, String> = emptyMap(),
        repeats: Int = 20,
    ): Pair<Long, Int> {
        var size = 0
        val times =
            (1..repeats + 2).map {
                val started = System.nanoTime()
                val r =
                    mockMvc
                        .perform(
                            request(HttpMethod.GET, "/api/v1$path").apply {
                                header("Authorization", "Bearer $access")
                                params.forEach { (k, v) -> param(k, v) }
                            },
                        ).andReturn()
                val elapsed = (System.nanoTime() - started) / 1_000_000
                check(status(r) == 200) { r.response.contentAsString }
                val json = body(r)
                size = if (json.isArray) json.size() else json["items"].size()
                elapsed
            }
        // первые два запроса — прогрев (план, кэш JVM)
        val sorted = times.drop(2).sorted()
        return sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)] to size
    }

    @Test
    fun `поиск и фильтры на ста тысячах рекомендаций укладываются в 300 мс`() {
        val author = newUser("perf_search")
        val g1 = createGroup(author, "Первая")["id"].asString()
        val g2 = createGroup(author, "Вторая")["id"].asString()
        val authorId = UUID.fromString(userId(author))

        jdbc
            .sql(
                """
                insert into reviews (id, author_id, title, description, rating, created_at, updated_at)
                select gen_random_uuid(), :author,
                       (array['Дюна','Матрица','Кофейня','Путешествие','Интерстеллар','Ресторан','Альбом','Сериал','Игра','Роман'])[1 + n % 10]
                         || ' ' || n,
                       (array['отличная книга про космос','хороший завтрак и кофе','путешествие по странам','фантастика будущего',
                              'любимая музыка для работы','семейный ресторан рядом','сильный сюжет и актёры','удобный сервис'])[1 + n % 8]
                         || ' ' || (array['лето','зима','вечер','утро','выходные'])[1 + n % 5],
                       1 + n % 10, now() - make_interval(secs => n), now()
                from generate_series(1, 100000) n
                """.trimIndent(),
            ).param("author", authorId)
            .update()
        jdbc
            .sql(
                """
                insert into review_groups (review_id, group_id, category_id, shared_at)
                select r.id, g.id, (select id from categories c where c.group_id = g.id order by c.sort_order limit 1 offset (abs(hashtext(r.id::text)) % 5)), r.created_at
                from reviews r
                join (select id, row_number() over (order by id) as rn from groups) g on g.rn = 1 + (abs(hashtext(r.id::text)) % 2)
                where r.author_id = :author
                """.trimIndent(),
            ).param("author", authorId)
            .update()
        jdbc.sql("insert into tags (name) select 'тег' || n from generate_series(1, 30) n").update()
        jdbc
            .sql(
                """
                insert into review_tags (review_id, tag_id)
                select r.id, t.id from (select id, row_number() over (order by id) rn from reviews) r
                join tags t on t.name = 'тег' || (1 + r.rn % 30)
                union
                select r.id, t.id from (select id, row_number() over (order by id) rn from reviews) r
                join tags t on t.name = 'тег' || (1 + (r.rn * 7) % 30)
                """.trimIndent(),
            ).update()
        jdbc.sql("analyze reviews").update()
        jdbc.sql("analyze review_groups").update()
        jdbc.sql("analyze review_tags").update()
        jdbc.sql("analyze tags").update()
        val total = jdbc.sql("select count(*) from review_groups").query(Int::class.java).single()
        assertEquals(100_000, total)
        val firstCategory = body(get("/groups/$g1/categories", author))[0]["id"].asString()
        val secondGroupCategory = body(get("/groups/$g2/categories", author))[0]["id"].asString()

        val cases =
            listOf(
                Triple("лента группы", "/groups/$g1/reviews", mapOf("limit" to "20")),
                Triple(
                    "лента: глубокая страница не нужна, второй запрос по курсору",
                    "/reviews",
                    mapOf("limit" to "20"),
                ),
                Triple("слово в другой форме", "/groups/$g1/reviews", mapOf("q" to "завтраки")),
                Triple("слово из описания", "/groups/$g1/reviews", mapOf("q" to "космос")),
                Triple("подстрока названия", "/groups/$g1/reviews", mapOf("q" to "терстел")),
                Triple("опечатка", "/groups/$g1/reviews", mapOf("q" to "Интерстелар")),
                Triple("поиск по всем группам", "/reviews", mapOf("q" to "ресторан")),
                Triple("категория с потомками", "/groups/$g1/reviews", mapOf("categoryIds" to firstCategory)),
                Triple("два тега", "/groups/$g1/reviews", mapOf("tags" to "тег3,тег15")),
                Triple("тег и текст", "/groups/$g1/reviews", mapOf("tags" to "тег3", "q" to "кофе")),
                Triple(
                    "автор и категория",
                    "/groups/$g2/reviews",
                    mapOf(
                        "authorId" to authorId.toString(),
                        "categoryIds" to secondGroupCategory,
                    ),
                ),
            )
        for ((name, path, params) in cases) {
            val (p95, size) = timed(author, path, params)
            println("ПОИСК p95=$p95 мс, строк=$size: $name")
            assertTrue(p95 < limitMs, "$name: p95 = $p95 мс")
        }
        val (tagsP95, _) = timed(author, "/groups/$g1/tags")
        println("ПОИСК p95=$tagsP95 мс: теги группы")
        assertTrue(tagsP95 < limitMs, "теги группы: p95 = $tagsP95 мс")
    }
}
