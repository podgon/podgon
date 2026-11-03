package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest : GroupsTestBase() {
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun addReview(
        access: String,
        title: String,
        description: String = "",
        tags: List<String> = emptyList(),
        groups: List<String> = emptyList(),
        categoryId: String = category("books.sf"),
        rating: Int = 8,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf(
                    "title" to title,
                    "description" to description,
                    "rating" to rating,
                    "categoryId" to categoryId,
                    "tags" to tags,
                    "groupIds" to groups,
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        clock.advance(Duration.ofSeconds(1))
        return body(r)["id"].asString()
    }

    /** GET с параметрами запроса, переданными как есть (без повторного кодирования шаблоном адреса). */
    private fun fetch(
        access: String,
        path: String,
    ) = mockMvc
        .perform(
            request(HttpMethod.GET, "/api/v1" + path.substringBefore('?')).apply {
                header("Authorization", "Bearer $access")
                path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach {
                    param(it.substringBefore('='), URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8))
                }
            },
        ).andReturn()

    private fun search(
        access: String,
        path: String,
    ): JsonNode {
        val r = fetch(access, path)
        check(status(r) == 200) { r.response.contentAsString }
        return body(r)
    }

    private fun titles(page: JsonNode) = page["items"].toList().map { it["title"].asString() }

    private fun nextCursor(page: JsonNode): String? = page.get("nextCursor")?.takeUnless { it.isNull }?.asString()

    @Test
    fun `морфология русского - по слову в другой форме находится рекомендация`() {
        val u = newUser("s_user1")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Дюна", "Отличная научная фантастика про пустыню", groups = listOf(g))
        addReview(u, "Кофейня", "Хороший завтрак", groups = listOf(g))
        assertEquals(listOf("Дюна"), titles(search(u, "/groups/$g/reviews?q=${enc("фантастики")}")))
        assertEquals(listOf("Кофейня"), titles(search(u, "/groups/$g/reviews?q=${enc("завтраки")}")))
        assertEquals(emptyList(), titles(search(u, "/groups/$g/reviews?q=${enc("неттакого")}")))
    }

    @Test
    fun `подстроки и опечатки в названии`() {
        val u = newUser("s_user2")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Интерстеллар", "Фильм", groups = listOf(g))
        addReview(u, "Матрица", "Фильм", groups = listOf(g))
        assertEquals(listOf("Интерстеллар"), titles(search(u, "/groups/$g/reviews?q=${enc("терстел")}")))
        assertEquals(listOf("Интерстеллар"), titles(search(u, "/groups/$g/reviews?q=${enc("Интерстелар")}")))
        assertEquals(listOf("Матрица"), titles(search(u, "/groups/$g/reviews?q=${enc("матрица")}")))
    }

    @Test
    fun `буквы ё и е в поиске не различаются`() {
        val u = newUser("s_user_yo")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Ёлки-палки", "Трактир с печёной картошкой", groups = listOf(g))
        addReview(u, "Пельменная", "Обычная еда", groups = listOf(g))
        // подстрока и опечатка в названии
        assertEquals(listOf("Ёлки-палки"), titles(search(u, "/groups/$g/reviews?q=${enc("елки")}")))
        assertEquals(listOf("Ёлки-палки"), titles(search(u, "/groups/$g/reviews?q=${enc("Елки-палки")}")))
        // полнотекстовый поиск по описанию в обе стороны
        assertEquals(listOf("Ёлки-палки"), titles(search(u, "/groups/$g/reviews?q=${enc("печеная")}")))
        assertEquals(listOf("Ёлки-палки"), titles(search(u, "/groups/$g/reviews?q=${enc("печёной")}")))
    }

    @Test
    fun `название весит больше описания и выдача упорядочена по релевантности`() {
        val u = newUser("s_user3")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Про путешествия", "Много слов про космос и звёзды, космос везде", groups = listOf(g))
        addReview(u, "Космос", "Кратко", groups = listOf(g))
        addReview(u, "Другое", "Один раз космос", groups = listOf(g))
        val result = titles(search(u, "/groups/$g/reviews?q=${enc("космос")}"))
        assertEquals("Космос", result.first(), "совпадение в названии выше совпадений в описании")
        assertEquals(3, result.size)
    }

    @Test
    fun `режим только по названиям не ищет в описаниях`() {
        val u = newUser("s_title_only")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Космос", "Кратко", groups = listOf(g))
        addReview(u, "Другое", "Один раз космос", groups = listOf(g))
        val q = enc("космоса")
        assertEquals(2, titles(search(u, "/groups/$g/reviews?q=$q")).size)
        assertEquals(listOf("Космос"), titles(search(u, "/groups/$g/reviews?q=$q&titleOnly=true")))
        assertEquals(listOf("Космос"), titles(search(u, "/reviews?q=$q&titleOnly=true")))
        val facets = search(u, "/groups/$g/facets?q=$q&titleOnly=true")
        assertEquals(1, facets["authors"][0]["count"].asInt())
    }

    @Test
    fun `спецсимволы запроса не работают как шаблоны`() {
        val u = newUser("s_user4")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Скидка 100% на всё", groups = listOf(g))
        addReview(u, "Обычная", groups = listOf(g))
        addReview(u, "Имя_с_подчёркиванием", groups = listOf(g))
        assertEquals(listOf("Скидка 100% на всё"), titles(search(u, "/groups/$g/reviews?q=${enc("100%")}")))
        // «%» и «_» ищутся как обычные символы, а не как шаблон «любой текст»
        assertEquals(listOf("Скидка 100% на всё"), titles(search(u, "/groups/$g/reviews?q=${enc("%")}")))
        assertEquals(listOf("Имя_с_подчёркиванием"), titles(search(u, "/groups/$g/reviews?q=${enc("_")}")))
        assertEquals(listOf("Имя_с_подчёркиванием"), titles(search(u, "/groups/$g/reviews?q=${enc("я_с_п")}")))
        assertEquals(emptyList(), titles(search(u, "/groups/$g/reviews?q=${enc("'; drop table reviews; --")}")))
        assertEquals(3, search(u, "/groups/$g/reviews?q=${enc("   ")}")["items"].size(), "пустой запрос — без поиска")
    }

    @Test
    fun `фильтр по категории включает подкатегории`() {
        val u = newUser("s_user5")
        val g = createGroup(u)["id"].asString()
        val parent = category("books")
        val child = category("books.sf")
        val other = category("film.movies")
        addReview(u, "В родителе", groups = listOf(g), categoryId = parent)
        addReview(u, "В дочерней", groups = listOf(g), categoryId = child)
        addReview(u, "В другой", groups = listOf(g), categoryId = other)
        assertEquals(
            setOf("В родителе", "В дочерней"),
            titles(search(u, "/groups/$g/reviews?categoryIds=$parent")).toSet(),
        )
        assertEquals(listOf("В дочерней"), titles(search(u, "/groups/$g/reviews?categoryIds=$child")))
        assertEquals(3, search(u, "/groups/$g/reviews?categoryIds=$parent,$other")["items"].size())
        assertTrue(
            search(u, "/reviews?categoryIds=$child")["items"].all { it["categoryId"].asString() == child },
        )
    }

    @Test
    fun `теги - все или любой и комбинация фильтров`() {
        val author = newUser("s_author6")
        val other = newUser("s_other6")
        val g = createGroup(author)["id"].asString()
        join(author, g, other)
        addReview(author, "Оба тега", tags = listOf("a", "b"), groups = listOf(g))
        addReview(author, "Только a", tags = listOf("a"), groups = listOf(g))
        addReview(other, "Чужой с a", tags = listOf("A"), groups = listOf(g))
        assertEquals(listOf("Оба тега"), titles(search(author, "/groups/$g/reviews?tags=a,b")))
        assertEquals(3, search(author, "/groups/$g/reviews?tags=a,b&tagMode=ANY")["items"].size())
        assertEquals(
            listOf("Чужой с a"),
            titles(search(author, "/groups/$g/reviews?tags=%23a&authorIds=${userId(other)}")),
        )
        assertEquals(
            listOf("Только a"),
            titles(search(author, "/groups/$g/reviews?tags=a&authorIds=${userId(author)}&q=${enc("только")}")),
        )
    }

    @Test
    fun `страницы поиска идут без повторов в обоих режимах а чужой курсор отвергается`() {
        val u = newUser("s_user7")
        val g = createGroup(u)["id"].asString()
        repeat(7) { addReview(u, "Книга номер $it", "Про книги", groups = listOf(g)) }

        for (query in listOf("", "&q=${enc("книга")}")) {
            val seen = mutableListOf<String>()
            var cursor: String? = null
            var pages = 0
            do {
                val page = search(u, "/groups/$g/reviews?limit=3$query" + (cursor?.let { "&cursor=$it" } ?: ""))
                seen += page["items"].toList().map { it["id"].asString() }
                cursor = nextCursor(page)
                pages++
            } while (cursor != null)
            assertEquals(7, seen.distinct().size, "режим '$query'")
            assertEquals(3, pages)
        }
        val dateCursor = nextCursor(search(u, "/groups/$g/reviews?limit=3"))!!
        invalidRequests {
            val wrongMode = fetch(u, "/groups/$g/reviews?q=${enc("книга")}&cursor=$dateCursor")
            assertEquals(400, status(wrongMode))
            assertEquals(400, status(get("/groups/$g/reviews?cursor=мусор", u)))
        }
    }

    @Test
    fun `поиск по нескольким группам и права`() {
        val u = newUser("s_user8")
        val stranger = newUser("s_stranger8")
        val g1 = createGroup(u, "Первая")["id"].asString()
        val g2 = createGroup(u, "Вторая")["id"].asString()
        val foreign = createGroup(stranger, "Чужая")["id"].asString()
        addReview(u, "Везде", groups = listOf(g1, g2))
        addReview(u, "Только первая", groups = listOf(g1))
        addReview(stranger, "Чужая рекомендация", groups = listOf(foreign))

        val all = search(u, "/reviews")
        assertEquals(listOf("Только первая", "Везде"), titles(all), "каждая рекомендация один раз (И15)")
        val everywhere = all["items"].toList().first { it["title"].asString() == "Везде" }
        assertEquals(setOf(g1, g2), everywhere["groupIds"].toList().map { it.asString() }.toSet())
        assertTrue("Чужая рекомендация" !in titles(all))
        val onlySecond = search(u, "/reviews?groupIds=$g2")
        assertEquals(listOf("Везде"), titles(onlySecond))
        assertEquals(listOf(g2), onlySecond["items"][0]["groupIds"].toList().map { it.asString() })
        assertEquals(1, search(u, "/reviews?q=${enc("первая")}")["items"].size())
        assertEquals(404, status(get("/reviews?groupIds=$foreign", u)))
        assertEquals(404, status(get("/reviews?groupIds=$g1,$foreign", u)))

        val pagedIds = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = search(u, "/reviews?limit=2" + (cursor?.let { "&cursor=$it" } ?: ""))
            pagedIds += page["items"].toList().map { it["id"].asString() }
            cursor = nextCursor(page)
        } while (cursor != null)
        assertEquals(2, pagedIds.size)
        assertEquals(2, pagedIds.distinct().size)
    }

    @Test
    fun `на Все порядок и дата по самой свежей публикации`() {
        val u = newUser("s_user8b")
        val g1 = createGroup(u, "Первая")["id"].asString()
        val g2 = createGroup(u, "Вторая")["id"].asString()
        val old = addReview(u, "Старая", groups = listOf(g1))
        addReview(u, "Новая", groups = listOf(g1))
        assertEquals(listOf("Новая", "Старая"), titles(search(u, "/reviews")))

        clock.advance(Duration.ofMinutes(5))
        val shared =
            call(HttpMethod.PUT, "/reviews/$old/groups", u, mapOf("groupIds" to listOf(g1, g2)))
        assertEquals(200, status(shared))
        val all = search(u, "/reviews")
        assertEquals(listOf("Старая", "Новая"), titles(all), "свежая публикация поднимает рекомендацию")
        val card = all["items"][0]
        assertTrue(card["sharedAt"].asString() > card["createdAt"].asString())
        assertEquals(listOf("Новая", "Старая"), titles(search(u, "/groups/$g1/reviews")), "в группе — своя дата")
    }

    @Test
    fun `пользователь без групп получает пустой результат`() {
        val u = newUser("s_user9")
        assertEquals(0, search(u, "/reviews")["items"].size())
    }

    @Test
    fun `теги группы с числом публикаций и автодополнение`() {
        val u = newUser("s_user10")
        val outsider = newUser("s_out10")
        val g = createGroup(u)["id"].asString()
        addReview(u, "Один", tags = listOf("фантастика", "книги"), groups = listOf(g))
        addReview(u, "Два", tags = listOf("фантастика"), groups = listOf(g))
        addReview(u, "Личный", tags = listOf("секрет"))
        val tags = search(u, "/groups/$g/tags")
        assertEquals(
            listOf("фантастика" to 2, "книги" to 1),
            tags.toList().map {
                it["name"].asString() to
                    it["count"].asInt()
            },
        )
        assertEquals(
            listOf("книги"),
            search(u, "/groups/$g/tags?prefix=${enc("#КН")}").toList().map { it["name"].asString() },
        )
        assertEquals(404, status(get("/groups/$g/tags", outsider)))
    }

    @Test
    fun `неверные параметры дают 400`() {
        val u = newUser("s_user11")
        val g = createGroup(u)["id"].asString()
        invalidRequests {
            assertEquals(400, status(get("/groups/$g/reviews?q=${"я".repeat(101)}", u)))
            assertEquals(400, status(get("/groups/$g/reviews?tagMode=SOME", u)))
            assertEquals(400, status(get("/groups/$g/reviews?categoryIds=не-uuid", u)))
            assertEquals(400, status(get("/groups/$g/reviews?authorIds=не-uuid", u)))
            assertEquals(400, status(get("/groups/$g/reviews?tags=${(1..11).joinToString(",") { "t$it" }}", u)))
        }
    }

    @Test
    fun `системные группы читаются только владельцем и не входят во Все`() {
        val u = newUser("s_user10")
        val other = newUser("s_other10")
        val g = createGroup(u)["id"].asString()
        val personal = personalGroup(u)
        addReview(u, "Личная ёлка", tags = listOf("ёлка"))
        addReview(u, "Групповая", tags = listOf("елка"), groups = listOf(g))

        assertEquals(listOf("Групповая"), titles(search(u, "/reviews")), "«Личное» не входит во «Все»")
        assertEquals(listOf("Личная ёлка"), titles(search(u, "/reviews?groupIds=$personal")))
        assertEquals(listOf("Личная ёлка"), titles(search(u, "/groups/$personal/reviews?q=${enc("елка")}")))
        assertEquals(
            listOf("елка"),
            body(get("/groups/$personal/tags", u)).toList().map { it["name"].asString() },
            "«ё» и «е» в тегах не различаются",
        )
        assertEquals(1, search(u, "/groups/$g/reviews?tags=${enc("ёлка")}")["items"].size())
        assertEquals(0, body(get("/groups/${favoritesGroup(u)}/reviews", u))["items"].size())
        assertEquals(404, status(get("/groups/$personal/reviews", other)))
        assertEquals(404, status(get("/groups/$personal/tags", other)))
        assertEquals(404, status(get("/reviews?groupIds=$personal", other)))
    }
}
