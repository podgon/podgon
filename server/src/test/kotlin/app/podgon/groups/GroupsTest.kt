package app.podgon.groups

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupsTest : GroupsTestBase() {
    @Test
    fun `создатель группы становится администратором, появляются системные роли`() {
        val owner = newUser("owner1")
        val group = createGroup(owner)
        assertEquals("Книжный клуб", group["name"].asString())
        assertEquals(1, group["memberCount"].asInt())
        assertEquals("ADMIN", group["myRole"]["code"].asString())
        assertTrue(group["myRole"]["admin"].asBoolean())

        val roles = body(get("/groups/${group["id"].asString()}/roles", owner))
        assertEquals(listOf("ADMIN", "MEMBER"), roles.toList().map { it["code"].asString() })
        assertTrue(roles.all { it["system"].asBoolean() })

        val list = body(get("/groups", owner))
        assertEquals(1, list.size())
        assertEquals(group["id"].asString(), list[0]["id"].asString())
    }

    @Test
    fun `валидация названия и описания группы`() {
        val owner = newUser("owner2")
        invalidRequests {
            val blank = call(HttpMethod.POST, "/groups", owner, mapOf("name" to "   "))
            assertEquals(400, status(blank))
            assertEquals("VALIDATION_FAILED", code(blank))
            val long =
                call(
                    HttpMethod.POST,
                    "/groups",
                    owner,
                    mapOf(
                        "name" to "я".repeat(101),
                        "description" to "x".repeat(501),
                    ),
                )
            assertEquals(400, status(long))
            assertEquals(2, body(long)["errors"].size())
        }
    }

    @Test
    fun `чужая группа не видна не участнику - везде 404`() {
        val owner = newUser("owner3")
        val outsider = newUser("outsider3")
        val id = createGroup(owner)["id"].asString()
        val someone = userId(owner)

        assertEquals(0, body(get("/groups", outsider)).size())
        assertEquals(404, status(get("/groups/$id", outsider)))
        assertEquals(404, status(call(HttpMethod.PATCH, "/groups/$id", outsider, mapOf("name" to "Взлом"))))
        assertEquals(404, status(delete("/groups/$id?confirm=true", outsider)))
        assertEquals(404, status(get("/groups/$id/members", outsider)))
        assertEquals(404, status(get("/groups/$id/roles", outsider)))
        assertEquals(404, status(get("/groups/$id/invitations", outsider)))
        assertEquals(404, status(call(HttpMethod.POST, "/groups/$id/invitations", outsider, mapOf("maxUses" to 1))))
        assertEquals(404, status(call(HttpMethod.POST, "/groups/$id/leave", outsider)))
        assertEquals(404, status(delete("/groups/$id/members/$someone", outsider)))
        assertEquals(
            404,
            status(
                call(
                    HttpMethod.POST,
                    "/groups/$id/roles",
                    outsider,
                    mapOf(
                        "title" to "Хакер",
                        "admin" to true,
                    ),
                ),
            ),
        )
        // несуществующая и невалидная группа выглядят так же
        assertEquals(404, status(get("/groups/00000000-0000-0000-0000-0000000000aa", owner)))
        invalidRequests { assertEquals(404, status(get("/groups/not-a-uuid", owner))) }
        // группа осталась нетронутой
        assertEquals("Книжный клуб", body(get("/groups/$id", owner))["name"].asString())
    }

    @Test
    fun `участник без прав администратора получает 403 на управляющие действия`() {
        val owner = newUser("owner4")
        val member = newUser("member4")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)
        val ownerId = userId(owner)
        val memberRole = roleId(member, id, "MEMBER")

        assertEquals(200, status(get("/groups/$id", member)))
        assertEquals(200, status(get("/groups/$id/members", member)))
        assertEquals(403, status(call(HttpMethod.PATCH, "/groups/$id", member, mapOf("name" to "Новое"))))
        assertEquals(403, status(delete("/groups/$id?confirm=true", member)))
        assertEquals(403, status(delete("/groups/$id/members/$ownerId", member)))
        assertEquals(403, status(setRole(member, id, userId(member), roleId(member, id, "ADMIN"))))
        assertEquals(403, status(call(HttpMethod.POST, "/groups/$id/roles", member, mapOf("title" to "Моя"))))
        assertEquals(403, status(call(HttpMethod.POST, "/groups/$id/invitations", member, mapOf("maxUses" to 1))))
        assertEquals(403, status(get("/groups/$id/invitations", member)))
        assertEquals("Книжный клуб", body(get("/groups/$id", owner))["name"].asString())
        assertEquals("MEMBER", body(get("/groups/$id", member))["myRole"]["code"].asString())
        assertEquals(memberRole, body(get("/groups/$id", member))["myRole"]["id"].asString())
    }

    @Test
    fun `администратор меняет название и описание, частично`() {
        val owner = newUser("owner5")
        val id = createGroup(owner)["id"].asString()
        val renamed = body(call(HttpMethod.PATCH, "/groups/$id", owner, mapOf("name" to "Новое название")))
        assertEquals("Новое название", renamed["name"].asString())
        assertEquals("Читаем вместе", renamed["description"].asString())
        val cleared = body(call(HttpMethod.PATCH, "/groups/$id", owner, mapOf("description" to "")))
        assertEquals("", cleared["description"].asString())
        assertEquals("Новое название", cleared["name"].asString())
    }

    @Test
    fun `удаление группы требует подтверждения и убирает всё`() {
        val owner = newUser("owner6")
        val member = newUser("member6")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)
        invite(owner, id)

        val unconfirmed = delete("/groups/$id", owner)
        assertEquals(400, status(unconfirmed))
        assertEquals("CONFIRMATION_REQUIRED", code(unconfirmed))
        assertEquals(200, status(get("/groups/$id", owner)))

        assertEquals(204, status(delete("/groups/$id?confirm=true", owner)))
        assertEquals(404, status(get("/groups/$id", owner)))
        assertEquals(404, status(get("/groups/$id", member)))
        for (table in listOf("group_roles", "memberships", "invitations")) {
            val left =
                jdbc
                    .sql(
                        "select count(*) from $table where group_id = :g",
                    ).param("g", java.util.UUID.fromString(id))
                    .query(Int::class.java)
                    .single()
            assertEquals(0, left, table)
        }
    }

    @Test
    fun `выход из группы - единственный администратор выйти не может`() {
        val owner = newUser("owner7")
        val member = newUser("member7")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)

        val blocked = call(HttpMethod.POST, "/groups/$id/leave", owner)
        assertEquals(409, status(blocked))
        assertEquals("LAST_ADMIN", code(blocked))
        assertEquals(2, members(owner, id).size())

        assertEquals(204, status(call(HttpMethod.POST, "/groups/$id/leave", member)))
        assertEquals(404, status(get("/groups/$id", member)))
        assertEquals(1, body(get("/groups/$id", owner))["memberCount"].asInt())

        // единственный участник-администратор тоже не может «просто выйти»: остаётся удалить группу
        assertEquals(409, status(call(HttpMethod.POST, "/groups/$id/leave", owner)))
    }

    @Test
    fun `администратор выходит, когда есть другой`() {
        val owner = newUser("owner8")
        val second = newUser("second8")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, second)
        assertEquals(200, status(setRole(owner, id, userId(second), roleId(owner, id, "ADMIN"))))
        assertEquals(204, status(call(HttpMethod.POST, "/groups/$id/leave", owner)))
        assertEquals("ADMIN", body(get("/groups/$id", second))["myRole"]["code"].asString())
    }

    @Test
    fun `удаление участника администратором`() {
        val owner = newUser("owner9")
        val member = newUser("member9")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)

        assertEquals(403, status(delete("/groups/$id/members/${userId(owner)}", owner)), "себя удалить нельзя")
        assertEquals(404, status(delete("/groups/$id/members/00000000-0000-0000-0000-0000000000bb", owner)))
        assertEquals(204, status(delete("/groups/$id/members/${userId(member)}", owner)))
        assertEquals(404, status(get("/groups/$id", member)))
        assertEquals(1, members(owner, id).size())
    }

    @Test
    fun `назначение ролей - последнего администратора понизить нельзя`() {
        val owner = newUser("owner10")
        val member = newUser("member10")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)
        val admin = roleId(owner, id, "ADMIN")
        val plain = roleId(owner, id, "MEMBER")

        val denied = setRole(owner, id, userId(owner), plain)
        assertEquals(409, status(denied))
        assertEquals("LAST_ADMIN", code(denied))
        assertEquals("ADMIN", body(get("/groups/$id", owner))["myRole"]["code"].asString())

        val promoted = setRole(owner, id, userId(member), admin)
        assertEquals(200, status(promoted))
        assertEquals("ADMIN", body(promoted)["role"]["code"].asString())
        assertEquals(
            200,
            status(setRole(member, id, userId(owner), plain)),
            "теперь два администратора — можно понизить",
        )

        assertEquals(404, status(setRole(member, id, userId(member), "00000000-0000-0000-0000-0000000000cc")))
        assertEquals(
            404,
            status(
                call(
                    HttpMethod.PUT,
                    "/groups/$id/members/${userId(member)}/role",
                    member,
                    mapOf(
                        "roleId" to "bad",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `роль из другой группы назначить нельзя`() {
        val ownerA = newUser("ownera11")
        val ownerB = newUser("ownerb11")
        val groupA = createGroup(ownerA)["id"].asString()
        val groupB = createGroup(ownerB, "Другая")["id"].asString()
        val foreignRole = roleId(ownerB, groupB, "ADMIN")
        assertEquals(404, status(setRole(ownerA, groupA, userId(ownerA), foreignRole)))
        assertEquals("ADMIN", body(get("/groups/$groupA", ownerA))["myRole"]["code"].asString())
    }

    @Test
    fun `пользовательские роли - создание, назначение, изменение, удаление`() {
        val owner = newUser("owner12")
        val member = newUser("member12")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)

        val created =
            call(
                HttpMethod.POST,
                "/groups/$id/roles",
                owner,
                mapOf(
                    "title" to "Модератор",
                    "description" to "Следит",
                    "admin" to true,
                ),
            )
        assertEquals(201, status(created))
        val role = body(created)
        assertEquals(false, role["system"].asBoolean())
        assertTrue(role["admin"].asBoolean())
        val roleId = role["id"].asString()

        assertEquals(200, status(setRole(owner, id, userId(member), roleId)))
        assertTrue(body(get("/groups/$id", member))["myRole"]["admin"].asBoolean(), "роль даёт права администратора")

        val renamed =
            body(
                call(
                    HttpMethod.PATCH,
                    "/groups/$id/roles/$roleId",
                    owner,
                    mapOf(
                        "title" to "Старший модератор",
                    ),
                ),
            )
        assertEquals("Старший модератор", renamed["title"].asString())

        // лишаем роль прав — участник перестаёт быть админом, владелец остаётся
        val demoted = call(HttpMethod.PATCH, "/groups/$id/roles/$roleId", owner, mapOf("admin" to false))
        assertEquals(200, status(demoted))
        assertEquals(403, status(call(HttpMethod.PATCH, "/groups/$id", member, mapOf("name" to "x"))))

        // удаление роли переводит участников в MEMBER
        assertEquals(204, status(delete("/groups/$id/roles/$roleId", owner)))
        assertEquals("MEMBER", body(get("/groups/$id", member))["myRole"]["code"].asString())
        assertEquals(2, body(get("/groups/$id/roles", owner)).size())
    }

    @Test
    fun `системные роли менять и удалять нельзя`() {
        val owner = newUser("owner13")
        val id = createGroup(owner)["id"].asString()
        for (code in listOf("ADMIN", "MEMBER")) {
            val roleId = roleId(owner, id, code)
            assertEquals(
                403,
                status(call(HttpMethod.PATCH, "/groups/$id/roles/$roleId", owner, mapOf("title" to "Взлом"))),
                code,
            )
            assertEquals(403, status(delete("/groups/$id/roles/$roleId", owner)), code)
        }
        invalidRequests { assertEquals(404, status(delete("/groups/$id/roles/not-a-uuid", owner))) }
    }

    @Test
    fun `лишение админских прав последней админской роли отклоняется`() {
        val owner = newUser("owner14")
        val member = newUser("member14")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, member)
        val custom =
            body(
                call(HttpMethod.POST, "/groups/$id/roles", owner, mapOf("title" to "Помощник", "admin" to true)),
            )["id"].asString()
        assertEquals(200, status(setRole(owner, id, userId(member), custom)))
        // владелец понижает себя: админом остаётся только участник с пользовательской ролью
        assertEquals(200, status(setRole(member, id, userId(owner), roleId(owner, id, "MEMBER"))))

        val blocked = call(HttpMethod.PATCH, "/groups/$id/roles/$custom", member, mapOf("admin" to false))
        assertEquals(409, status(blocked))
        assertEquals("LAST_ADMIN", code(blocked))
        val blockedDelete = delete("/groups/$id/roles/$custom", member)
        assertEquals(409, status(blockedDelete))
        assertEquals("LAST_ADMIN", code(blockedDelete))
        assertTrue(body(get("/groups/$id", member))["myRole"]["admin"].asBoolean(), "изменение откатилось")
    }

    @Test
    fun `два администратора одновременно понижают друг друга - администратор остаётся`() {
        val owner = newUser("owner15")
        val other = newUser("other15")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, other)
        val admin = roleId(owner, id, "ADMIN")
        val plain = roleId(owner, id, "MEMBER")
        assertEquals(200, status(setRole(owner, id, userId(other), admin)))

        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(2)
        try {
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val results =
                listOf(
                    pool.submit<Int> {
                        barrier.await()
                        status(setRole(owner, id, userId(other), plain))
                    },
                    pool.submit<Int> {
                        barrier.await()
                        status(setRole(other, id, userId(owner), plain))
                    },
                ).map { it.get() }
            // первый запрос выполняется, второй уже не администратор (403) либо упирается в инвариант (409)
            assertTrue(results.count { it == 200 } == 1 && results.any { it == 403 || it == 409 }, "статусы: $results")
        } finally {
            pool.shutdownNow()
        }
        val admins = members(owner, id).count { it["role"]["admin"].asBoolean() }
        assertEquals(1, admins, "в группе всегда остаётся администратор")
    }

    @Test
    fun `база сама защищает инвариант администратора`() {
        val owner = newUser("owner16")
        val member = newUser("member16")
        val id = java.util.UUID.fromString(createGroup(owner)["id"].asString())
        join(owner, id.toString(), member)

        fun failsAtCommit(sql: String) {
            val error =
                runCatching { jdbc.sql(sql).param("g", id).update() }.exceptionOrNull()
            assertTrue(
                error != null && error.message!!.contains("администратор"),
                "ожидали отказ БД: $sql, получили $error",
            )
        }
        failsAtCommit(
            "delete from memberships where group_id = :g and role_id in (select id from group_roles where code = 'ADMIN' and group_id = :g)",
        )
        // единственный админ держит пользовательскую роль: снятие с неё признака администратора ловит триггер
        val custom =
            body(
                call(HttpMethod.POST, "/groups/$id/roles", owner, mapOf("title" to "Помощник", "admin" to true)),
            )["id"].asString()
        assertEquals(200, status(setRole(owner, id.toString(), userId(member), custom)))
        assertEquals(200, status(setRole(member, id.toString(), userId(owner), roleId(owner, id.toString(), "MEMBER"))))
        failsAtCommit("update group_roles set is_admin = false where group_id = :g and code like 'R%'")
        assertEquals(2, members(member, id.toString()).size())
    }

    @Test
    fun `системная роль не может быть создана вручную с чужим кодом`() {
        val owner = newUser("owner17")
        val id = java.util.UUID.fromString(createGroup(owner)["id"].asString())
        val error =
            runCatching {
                jdbc
                    .sql(
                        "insert into group_roles (id, group_id, code, title, is_admin, is_system, created_at) " +
                            "values (gen_random_uuid(), :g, 'ADMIN', 'Двойник', false, false, now())",
                    ).param("g", id)
                    .update()
            }.exceptionOrNull()
        assertTrue(error != null, "пользовательская роль с кодом ADMIN должна быть отклонена")
    }

    @Test
    fun `без токена - 401`() {
        invalidRequests {
            assertEquals(401, status(get("/groups", null)))
            assertEquals(401, status(call(HttpMethod.POST, "/groups", null, mapOf("name" to "Анонимная"))))
            assertEquals(401, status(call(HttpMethod.POST, "/invitations/accept", null, mapOf("token" to "x"))))
        }
    }
}
