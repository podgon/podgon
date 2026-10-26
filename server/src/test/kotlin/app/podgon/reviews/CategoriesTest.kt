package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoriesTest : GroupsTestBase() {
    private fun categories(
        access: String,
        groupId: String,
    ): JsonNode = body(get("/groups/$groupId/categories", access))

    private fun addCategory(
        access: String,
        groupId: String,
        name: String,
        parentId: String? = null,
    ) = call(
        HttpMethod.POST,
        "/groups/$groupId/categories",
        access,
        mapOf("name" to name, "parentId" to parentId),
    )

    private fun created(
        access: String,
        groupId: String,
        name: String,
        parentId: String? = null,
    ): String {
        val r = addCategory(access, groupId, name, parentId)
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["id"].asString()
    }

    private fun move(
        access: String,
        groupId: String,
        id: String,
        parentId: String?,
    ) = call(HttpMethod.PUT, "/groups/$groupId/categories/$id/parent", access, mapOf("parentId" to parentId))

    private fun names(list: JsonNode) = list.toList().map { it["name"].asString() }

    @Test
    fun `новая группа получает копию шаблона с иерархией`() {
        val owner = newUser("cat_owner1")
        val groupId = createGroup(owner)["id"].asString()
        val list = categories(owner, groupId)
        assertEquals(10, list.size())
        val travel = list.first { it["name"].asString() in setOf("Путешествия", "Travel") }
        val children = list.filter { it["parentId"].asString("") == travel["id"].asString() }
        assertEquals(2, children.size)
        assertTrue(list.none { it["id"].asString() == "00000000-0000-4000-8000-000000000001" })
    }

    @Test
    fun `шаблон копируется на языке создателя`() {
        val owner = newUser("cat_owner2")
        jdbc.sql("update users set locale = 'en' where username = 'cat_owner2'").update()
        val groupId = createGroup(owner)["id"].asString()
        assertTrue("Books" in names(categories(owner, groupId)))
        jdbc.sql("update users set locale = 'ru' where username = 'cat_owner2'").update()
        val ruGroup = createGroup(owner, "Вторая")["id"].asString()
        assertTrue("Книги" in names(categories(owner, ruGroup)))
    }

    @Test
    fun `права - участник читает, но не меняет, чужой не видит`() {
        val admin = newUser("cat_admin3")
        val member = newUser("cat_member3")
        val outsider = newUser("cat_out3")
        val groupId = createGroup(admin)["id"].asString()
        join(admin, groupId, member)
        val anyId = categories(admin, groupId)[0]["id"].asString()

        assertEquals(10, categories(member, groupId).size())
        assertEquals(403, status(addCategory(member, groupId, "Нельзя")))
        assertEquals(
            403,
            status(
                call(
                    HttpMethod.PATCH,
                    "/groups/$groupId/categories/$anyId",
                    member,
                    mapOf(
                        "name" to "X",
                    ),
                ),
            ),
        )
        assertEquals(403, status(move(member, groupId, anyId, null)))
        assertEquals(403, status(get("/groups/$groupId/categories/$anyId/delete-impact", member)))
        assertEquals(403, status(delete("/groups/$groupId/categories/$anyId?confirm=true", member)))

        assertEquals(404, status(get("/groups/$groupId/categories", outsider)))
        assertEquals(404, status(addCategory(outsider, groupId, "Взлом")))
        assertEquals(404, status(delete("/groups/$groupId/categories/$anyId?confirm=true", outsider)))
    }

    @Test
    fun `категория другой группы недоступна по адресу своей группы`() {
        val owner = newUser("cat_owner4")
        val groupA = createGroup(owner, "A")["id"].asString()
        val groupB = createGroup(owner, "B")["id"].asString()
        val inB = categories(owner, groupB)[0]["id"].asString()
        assertEquals(
            404,
            status(call(HttpMethod.PATCH, "/groups/$groupA/categories/$inB", owner, mapOf("name" to "X"))),
        )
        assertEquals(404, status(addCategory(owner, groupA, "Новая", inB)))
        assertEquals(404, status(move(owner, groupA, categories(owner, groupA)[0]["id"].asString(), inB)))
        assertEquals(404, status(delete("/groups/$groupA/categories/$inB?confirm=true", owner)))
        invalidRequests {
            assertEquals(404, status(get("/groups/$groupA/categories/not-a-uuid/delete-impact", owner)))
        }
    }

    @Test
    fun `создание переименование и уникальность имён среди соседей`() {
        val owner = newUser("cat_owner5")
        val groupId = createGroup(owner)["id"].asString()
        val id = created(owner, groupId, "  Настолки  ")
        assertTrue("Настолки" in names(categories(owner, groupId)))

        assertEquals("CATEGORY_NAME_TAKEN", code(addCategory(owner, groupId, "настолки")))
        val child = created(owner, groupId, "Настолки", id)
        val renamed =
            call(
                HttpMethod.PATCH,
                "/groups/$groupId/categories/$id",
                owner,
                mapOf(
                    "description" to "Игры за столом",
                ),
            )
        assertEquals(200, status(renamed))
        assertEquals("Игры за столом", body(renamed)["description"].asString())
        assertEquals("Настолки", body(renamed)["name"].asString())
        // то же имя в разных ветках допустимо, на одном уровне — нет
        val other = created(owner, groupId, "Хобби")
        val clash = call(HttpMethod.PATCH, "/groups/$groupId/categories/$other", owner, mapOf("name" to "НАСТОЛКИ"))
        assertEquals("CATEGORY_NAME_TAKEN", code(clash))
        assertEquals(
            200,
            status(
                call(
                    HttpMethod.PATCH,
                    "/groups/$groupId/categories/$child",
                    owner,
                    mapOf(
                        "name" to "Хобби",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `валидация названия и описания`() {
        val owner = newUser("cat_owner6")
        val groupId = createGroup(owner)["id"].asString()
        invalidRequests {
            val blank = addCategory(owner, groupId, "   ")
            assertEquals(400, status(blank))
            assertEquals("VALIDATION_FAILED", code(blank))
            val long =
                call(
                    HttpMethod.POST,
                    "/groups/$groupId/categories",
                    owner,
                    mapOf("name" to "я".repeat(101), "description" to "x".repeat(501)),
                )
            assertEquals(400, status(long))
            assertEquals(2, body(long)["errors"].size())
        }
    }

    @Test
    fun `перенос поддерева и запрет цикла`() {
        val owner = newUser("cat_owner7")
        val groupId = createGroup(owner)["id"].asString()
        val a = created(owner, groupId, "A")
        val b = created(owner, groupId, "B", a)
        val c = created(owner, groupId, "C", b)

        assertEquals("CATEGORY_CYCLE", code(move(owner, groupId, a, a)))
        assertEquals("CATEGORY_CYCLE", code(move(owner, groupId, a, b)))
        assertEquals("CATEGORY_CYCLE", code(move(owner, groupId, a, c)))
        assertEquals(409, status(move(owner, groupId, b, c)))

        val moved = move(owner, groupId, b, null)
        assertEquals(200, status(moved))
        assertTrue(body(moved)["parentId"].isNull)
        // потомок поехал вместе с родителем
        assertEquals(b, categories(owner, groupId).first { it["id"].asString() == c }["parentId"].asString())
        assertEquals(200, status(move(owner, groupId, a, c)))
    }

    @Test
    fun `глубина иерархии ограничена`() {
        val owner = newUser("cat_owner8")
        val groupId = createGroup(owner)["id"].asString()
        var parent: String? = null
        val chain = (1..5).map { level -> created(owner, groupId, "Уровень $level", parent).also { parent = it } }
        assertEquals("CATEGORY_TOO_DEEP", code(addCategory(owner, groupId, "Уровень 6", chain.last())))

        // перенос поддерева, которое после переноса стало бы глубже 5
        val extra = created(owner, groupId, "Ветка")
        created(owner, groupId, "Лист", extra)
        assertEquals("CATEGORY_TOO_DEEP", code(move(owner, groupId, extra, chain[3])))
        assertEquals(200, status(move(owner, groupId, extra, chain[2])))
    }

    @Test
    fun `удаление с подкатегориями требует подтверждения и каскадом удаляет поддерево`() {
        val owner = newUser("cat_owner9")
        val groupId = createGroup(owner)["id"].asString()
        val a = created(owner, groupId, "A")
        val b = created(owner, groupId, "B", a)
        created(owner, groupId, "C", b)
        val before = categories(owner, groupId).size()

        val impact = body(get("/groups/$groupId/categories/$a/delete-impact", owner))
        assertEquals(2, impact["subcategories"].asInt())
        assertEquals(0, impact["publications"].asInt())

        val refused = delete("/groups/$groupId/categories/$a", owner)
        assertEquals(400, status(refused))
        assertEquals("CONFIRMATION_REQUIRED", code(refused))
        assertEquals(before, categories(owner, groupId).size())

        assertEquals(204, status(delete("/groups/$groupId/categories/$a?confirm=true", owner)))
        assertEquals(before - 3, categories(owner, groupId).size())
        assertEquals(404, status(delete("/groups/$groupId/categories/$a?confirm=true", owner)))
    }

    @Test
    fun `пустую категорию можно удалить без подтверждения`() {
        val owner = newUser("cat_owner10")
        val groupId = createGroup(owner)["id"].asString()
        val leaf = created(owner, groupId, "Пустая")
        assertEquals(204, status(delete("/groups/$groupId/categories/$leaf", owner)))
    }

    @Test
    fun `удаление группы удаляет её категории и не трогает чужие`() {
        val owner = newUser("cat_owner11")
        val groupA = createGroup(owner, "A")["id"].asString()
        val groupB = createGroup(owner, "B")["id"].asString()
        assertEquals(204, status(delete("/groups/$groupA?confirm=true", owner)))
        assertEquals(10, categories(owner, groupB).size())
        assertEquals(10, jdbc.sql("select count(*) from categories").query(Int::class.java).single())
    }
}
