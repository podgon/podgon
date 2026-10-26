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

    private fun categoryId(
        access: String,
        groupId: String,
        index: Int = 0,
    ): String = body(get("/groups/$groupId/categories", access))[index]["id"].asString()

    private fun addReview(
        access: String,
        title: String,
        description: String = "",
        tags: List<String> = emptyList(),
        publications: List<Pair<String, String>> = emptyList(),
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/reviews",
                access,
                mapOf(
                    "title" to title,
                    "description" to description,
                    "rating" to 8,
                    "tags" to tags,
                    "publications" to publications.map { (g, c) -> mapOf("groupId" to g, "categoryId" to c) },
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
        val c = categoryId(u, g)
        addReview(u, "Дюна", "Отличная научная фантастика про пустыню", publications = listOf(g to c))
        addReview(u, "Кофейня", "Хороший завтрак", publications = listOf(g to c))
        assertEquals(listOf("Дюна"), titles(search(u, "/groups/$g/reviews?q=${enc("фантастики")}")))
        assertEquals(listOf("Кофейня"), titles(search(u, "/groups/$g/reviews?q=${enc("завтраки")}")))
        assertEquals(emptyList(), titles(search(u, "/groups/$g/reviews?q=${enc("неттакого")}")))
    }

    @Test
    fun `подстроки и опечатки в названии`() {
        val u = newUser("s_user2")
        val g = createGroup(u)["id"].asString()
        val c = categoryId(u, g)
        addReview(u, "Интерстеллар", "Фильм", publications = listOf(g to c))
        addReview(u, "Матрица", "Фильм", publications = listOf(g to c))
        assertEquals(listOf("Интерстеллар"), titles(search(u, "/groups/$g/reviews?q=${enc("терстел")}")))
        assertEquals(listOf("Интерстеллар"), titles(search(u, "/groups/$g/reviews?q=${enc("Интерстелар")}")))
        assertEquals(listOf("Матрица"), titles(search(u, "/groups/$g/reviews?q=${enc("матрица")}")))
    }

    @Test
    fun `буквы ё и е в поиске не различаются`() {
        val u = newUser("s_user_yo")
        val g = createGroup(u)["id"].asString()
        val c = categoryId(u, g)
        addReview(u, "Ёлки-палки", "Трактир с печёной картошкой", publications = listOf(g to c))
        addReview(u, "Пельменная", "Обычная еда", publications = listOf(g to c))
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
        val c = categoryId(u, g)
        addReview(u, "Про путешествия", "Много слов про космос и звёзды, космос везде", publications = listOf(g to c))
        addReview(u, "Космос", "Кратко", publications = listOf(g to c))
        addReview(u, "Другое", "Один раз космос", publications = listOf(g to c))
        val result = titles(search(u, "/groups/$g/reviews?q=${enc("космос")}"))
        assertEquals("Космос", result.first(), "совпадение в названии выше совпадений в описании")
        assertEquals(3, result.size)
    }

    @Test
    fun `спецсимволы запроса не работают как шаблоны`() {
        val u = newUser("s_user4")
        val g = createGroup(u)["id"].asString()
        val c = categoryId(u, g)
        addReview(u, "Скидка 100% на всё", publications = listOf(g to c))
        addReview(u, "Обычная", publications = listOf(g to c))
        addReview(u, "Имя_с_подчёркиванием", publications = listOf(g to c))
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
        val parent = categoryId(u, g, 0)
        val other = categoryId(u, g, 1)
        val child =
            body(
                call(HttpMethod.POST, "/groups/$g/categories", u, mapOf("name" to "Дочерняя", "parentId" to parent)),
            )["id"].asString()
        addReview(u, "В родителе", publications = listOf(g to parent))
        addReview(u, "В дочерней", publications = listOf(g to child))
        addReview(u, "В другой", publications = listOf(g to other))
        assertEquals(
            setOf("В родителе", "В дочерней"),
            titles(search(u, "/groups/$g/reviews?categoryIds=$parent")).toSet(),
        )
        assertEquals(listOf("В дочерней"), titles(search(u, "/groups/$g/reviews?categoryIds=$child")))
        assertEquals(3, search(u, "/groups/$g/reviews?categoryIds=$parent,$other")["items"].size())
    }

    @Test
    fun `теги - все или любой и комбинация фильтров`() {
        val author = newUser("s_author6")
        val other = newUser("s_other6")
        val g = createGroup(author)["id"].asString()
        join(author, g, other)
        val c = categoryId(author, g)
        addReview(author, "Оба тега", tags = listOf("a", "b"), publications = listOf(g to c))
        addReview(author, "Только a", tags = listOf("a"), publications = listOf(g to c))
        addReview(other, "Чужой с a", tags = listOf("A"), publications = listOf(g to c))
        assertEquals(listOf("Оба тега"), titles(search(author, "/groups/$g/reviews?tags=a,b")))
        assertEquals(3, search(author, "/groups/$g/reviews?tags=a,b&tagMode=ANY")["items"].size())
        assertEquals(
            listOf("Чужой с a"),
            titles(search(author, "/groups/$g/reviews?tags=%23a&authorId=${userId(other)}")),
        )
        assertEquals(
            listOf("Только a"),
            titles(search(author, "/groups/$g/reviews?tags=a&authorId=${userId(author)}&q=${enc("только")}")),
        )
    }

    @Test
    fun `страницы поиска идут без повторов в обоих режимах а чужой курсор отвергается`() {
        val u = newUser("s_user7")
        val g = createGroup(u)["id"].asString()
        val c = categoryId(u, g)
        repeat(7) { addReview(u, "Книга номер $it", "Про книги", publications = listOf(g to c)) }

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
        val c1 = categoryId(u, g1)
        val c2 = categoryId(u, g2)
        addReview(u, "Везде", publications = listOf(g1 to c1, g2 to c2))
        addReview(u, "Только первая", publications = listOf(g1 to c1))
        addReview(stranger, "Чужая рекомендация", publications = listOf(foreign to categoryId(stranger, foreign)))

        val all = search(u, "/reviews")
        assertEquals(3, all["items"].size(), "«Везде» — двумя карточками")
        assertEquals(setOf(g1, g2), all["items"].toList().map { it["groupId"].asString() }.toSet())
        assertTrue("Чужая рекомендация" !in titles(all))
        assertEquals(listOf("Везде"), titles(search(u, "/reviews?groupIds=$g2")))
        assertEquals(1, search(u, "/reviews?q=${enc("первая")}")["items"].size())
        assertEquals(404, status(get("/reviews?groupIds=$foreign", u)))
        assertEquals(404, status(get("/reviews?groupIds=$g1,$foreign", u)))

        val pagedIds = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = search(u, "/reviews?limit=2" + (cursor?.let { "&cursor=$it" } ?: ""))
            pagedIds += page["items"].toList().map { it["id"].asString() + it["groupId"].asString() }
            cursor = nextCursor(page)
        } while (cursor != null)
        assertEquals(3, pagedIds.distinct().size)
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
        val c = categoryId(u, g)
        addReview(u, "Один", tags = listOf("фантастика", "книги"), publications = listOf(g to c))
        addReview(u, "Два", tags = listOf("фантастика"), publications = listOf(g to c))
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
            assertEquals(400, status(get("/groups/$g/reviews?authorId=не-uuid", u)))
            assertEquals(400, status(get("/groups/$g/reviews?tags=${(1..11).joinToString(",") { "t$it" }}", u)))
        }
    }
}
