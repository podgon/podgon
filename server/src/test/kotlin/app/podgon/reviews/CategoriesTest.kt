package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Общий набор категорий (ADR-011): состав, языки, кэширование по `ETag`. */
class CategoriesTest : GroupsTestBase() {
    private fun categories(
        access: String?,
        query: String = "",
        ifNoneMatch: String? = null,
    ): MvcResult =
        mockMvc
            .perform(
                request(HttpMethod.GET, "/api/v1/categories$query").apply {
                    access?.let { header("Authorization", "Bearer $it") }
                    ifNoneMatch?.let { header("If-None-Match", it) }
                },
            ).andReturn()

    private fun names(set: JsonNode): Map<String, String> =
        set["items"].toList().associate { it["key"].asString() to it["name"].asString() }

    @Test
    fun `набор из двух уровней с Без категории в конце`() {
        val u = newUser("cat_set1")
        val set = body(categories(u, "?locale=ru"))
        val items = set["items"].toList()
        val ids = items.map { it["id"].asString() }.toSet()
        val roots = items.filter { it["parentId"] == null || it["parentId"].isNull }
        val children = items - roots.toSet()

        assertEquals(22, roots.size, "21 корень и «Без категории»")
        assertEquals(218, children.size)
        assertTrue(children.all { it["parentId"].asString() in ids && "." in it["key"].asString() })
        assertTrue(
            children.all { c -> roots.any { it["id"].asString() == c["parentId"].asString() } },
            "подкатегории только у корней",
        )
        val sortOrders = items.map { it["sortOrder"].asInt() }
        assertEquals(sortOrders.sorted(), sortOrders, "в порядке sortOrder")

        val none = items.last()
        assertEquals("none", none["key"].asString())
        assertTrue(none["system"].asBoolean())
        assertEquals("Без категории", none["name"].asString())
        assertEquals(1, items.count { it["system"].asBoolean() })
        assertEquals(category("books.sf"), items.first { it["key"].asString() == "books.sf" }["id"].asString())
    }

    @Test
    fun `язык из параметра или из профиля`() {
        val u = newUser("cat_set2")
        val ru = body(categories(u, "?locale=ru"))
        val en = body(categories(u, "?locale=en"))
        assertEquals("Без категории", names(ru)["none"])
        assertEquals("Uncategorized", names(en)["none"])
        assertEquals(names(ru).keys, names(en).keys)
        assertNotEquals(ru["version"].asString(), en["version"].asString())

        assertEquals(names(ru), names(body(categories(u))), "без locale — язык профиля")
        assertEquals(200, status(call(HttpMethod.PATCH, "/me", u, mapOf("locale" to "en"))))
        assertEquals(names(en), names(body(categories(u))))
        invalidRequests {
            assertEquals(400, status(categories(u, "?locale=de")))
        }
    }

    @Test
    fun `повторный запрос с той же версией получает 304`() {
        val u = newUser("cat_set3")
        val first = categories(u, "?locale=ru")
        val etag = first.response.getHeader("ETag")!!
        assertEquals("\"${body(first)["version"].asString()}\"", etag)

        val same = categories(u, "?locale=ru", ifNoneMatch = etag)
        assertEquals(304, status(same))
        assertEquals("", same.response.contentAsString)
        assertEquals(etag, same.response.getHeader("ETag"))
        assertEquals(304, status(categories(u, "?locale=ru", ifNoneMatch = "W/$etag")))

        assertEquals(200, status(categories(u, "?locale=ru", ifNoneMatch = "\"старая\"")))
        assertEquals(200, status(categories(u, "?locale=en", ifNoneMatch = etag)), "другой язык — другая версия")
    }

    @Test
    fun `без входа набор недоступен`() {
        invalidRequests {
            assertEquals(401, status(categories(null)))
        }
    }
}
