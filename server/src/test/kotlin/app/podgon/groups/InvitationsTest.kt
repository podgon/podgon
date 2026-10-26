package app.podgon.groups

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InvitationsTest : GroupsTestBase() {
    private fun preview(
        access: String,
        token: String,
    ) = call(HttpMethod.POST, "/invitations/preview", access, mapOf("token" to token))

    @Test
    fun `вступление по коду - роль MEMBER, одноразовый код исчерпывается`() {
        val owner = newUser("inv_owner1")
        val first = newUser("inv_first1")
        val second = newUser("inv_second1")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id)

        val joined = accept(first, token)
        assertEquals(200, status(joined))
        assertEquals(id, body(joined)["id"].asString())
        assertEquals("MEMBER", body(joined)["myRole"]["code"].asString())
        assertEquals(2, body(joined)["memberCount"].asInt())

        val again = accept(first, token)
        assertEquals(409, status(again))
        assertEquals("ALREADY_MEMBER", code(again))

        val exhausted = accept(second, token)
        assertEquals(400, status(exhausted))
        assertEquals("INVALID_INVITATION", code(exhausted))
        assertEquals(404, status(get("/groups/$id", second)))
    }

    @Test
    fun `код принимается при вводе без дефисов и в любом регистре`() {
        val owner = newUser("inv_owner11")
        val guest = newUser("inv_guest11")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id)
        val typed = " " + token.lowercase().replace("-", " ") + " "
        assertEquals(200, status(accept(guest, typed)))
        assertEquals(2, members(owner, id).size())
    }

    @Test
    fun `многоразовое приглашение принимает до maxUses человек`() {
        val owner = newUser("inv_owner2")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id, maxUses = 2)
        assertEquals(200, status(accept(newUser("inv_a2"), token)))
        assertEquals(200, status(accept(newUser("inv_b2"), token)))
        assertEquals("INVALID_INVITATION", code(accept(newUser("inv_c2"), token)))
        assertEquals(3, members(owner, id).size())
    }

    @Test
    fun `адресное приглашение уходит письмом и принимается только владельцем адреса`() {
        val owner = newUser("inv_owner3")
        val target = newUser("inv_target3")
        val other = newUser("inv_other3")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id, email = "  INV_Target3@Example.com ")

        val queued =
            jdbc
                .sql(
                    "select to_address, payload ->> 'token' as token, payload ->> 'groupName' as g from mail_outbox where template = 'GROUP_INVITATION'",
                ).query { rs, _ -> Triple(rs.getString("to_address"), rs.getString("token"), rs.getString("g")) }
                .list()
                .single()
        assertEquals("inv_target3@example.com", queued.first)
        assertEquals(token, queued.second)
        assertEquals("Книжный клуб", queued.third)

        val denied = accept(other, token)
        assertEquals(400, status(denied))
        assertEquals("INVALID_INVITATION", code(denied))
        assertEquals("INVALID_INVITATION", code(preview(other, token)))
        assertEquals(404, status(get("/groups/$id", other)))

        assertEquals(200, status(accept(target, token)))
        assertEquals(2, members(owner, id).size())
    }

    @Test
    fun `токен хранится только хэшем и не возвращается в списке`() {
        val owner = newUser("inv_owner4")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id)

        val stored =
            jdbc
                .sql("select token_hash from invitations")
                .query { rs, _ -> rs.getBytes("token_hash") }
                .list()
                .single()
        assertEquals(32, stored.size)
        assertNotEquals(token, String(stored, Charsets.UTF_8))
        assertTrue(Regex("^([0-9A-HJKMNP-TV-Z]{4}-){5}[0-9A-HJKMNP-TV-Z]{4}$").matches(token), "читаемый код: $token")

        val listing = get("/groups/$id/invitations", owner)
        assertFalse(listing.response.contentAsString.contains(token), "список не раскрывает токен")
        val info = body(listing)[0]
        assertTrue(info["active"].asBoolean())
        assertEquals(0, info["usedCount"].asInt())
        assertEquals("inv_owner4", info["createdBy"].asString())
    }

    @Test
    fun `отозванное и просроченное приглашения не работают`() {
        val owner = newUser("inv_owner5")
        val id = createGroup(owner)["id"].asString()

        val revokedToken = invite(owner, id)
        val invitationId = body(get("/groups/$id/invitations", owner))[0]["id"].asString()
        assertEquals(204, status(delete("/groups/$id/invitations/$invitationId", owner)))
        assertEquals(204, status(delete("/groups/$id/invitations/$invitationId", owner)), "отзыв идемпотентен")
        assertEquals("INVALID_INVITATION", code(accept(newUser("inv_late5"), revokedToken)))
        val info = body(get("/groups/$id/invitations", owner))[0]
        assertTrue(info["revoked"].asBoolean())
        assertFalse(info["active"].asBoolean())

        val shortToken =
            body(
                call(HttpMethod.POST, "/groups/$id/invitations", owner, mapOf("maxUses" to 1, "ttlHours" to 1)),
            )["token"].asString()
        assertEquals(404, status(delete("/groups/$id/invitations/00000000-0000-0000-0000-0000000000dd", owner)))
        // access-токен владельца живёт 15 минут, поэтому проверки с его токеном выше
        clock.advance(Duration.ofMinutes(61))
        assertEquals("INVALID_INVITATION", code(accept(newUser("inv_late5b"), shortToken)))
    }

    @Test
    fun `предпросмотр показывает группу и автора, но не вступает`() {
        val owner = newUser("inv_owner6")
        val guest = newUser("inv_guest6")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id)

        val r = preview(guest, token)
        assertEquals(200, status(r))
        assertEquals("Книжный клуб", body(r)["groupName"].asString())
        assertEquals("Читаем вместе", body(r)["groupDescription"].asString())
        assertEquals(1, body(r)["memberCount"].asInt())
        assertTrue(body(r)["invitedBy"].asString().isNotBlank())
        assertEquals(404, status(get("/groups/$id", guest)))
        assertEquals("INVALID_INVITATION", code(preview(guest, "неверный-код")))
    }

    @Test
    fun `чужой администратор не управляет приглашениями этой группы`() {
        val ownerA = newUser("inv_ownera7")
        val ownerB = newUser("inv_ownerb7")
        val groupA = createGroup(ownerA)["id"].asString()
        val groupB = createGroup(ownerB, "Другая")["id"].asString()
        invite(ownerA, groupA)
        val invitationId = body(get("/groups/$groupA/invitations", ownerA))[0]["id"].asString()

        assertEquals(404, status(get("/groups/$groupA/invitations", ownerB)))
        assertEquals(404, status(delete("/groups/$groupA/invitations/$invitationId", ownerB)))
        // приглашение группы A не отзывается через группу B
        assertEquals(404, status(delete("/groups/$groupB/invitations/$invitationId", ownerB)))
        assertTrue(body(get("/groups/$groupA/invitations", ownerA))[0]["active"].asBoolean())
    }

    @Test
    fun `заблокированный пользователь не вступает`() {
        val owner = newUser("inv_owner8")
        val blocked = newUser("inv_blocked8")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id)
        jdbc.sql("update users set status = 'BLOCKED' where username = 'inv_blocked8'").update()
        val r = accept(blocked, token)
        assertEquals(403, status(r))
        assertEquals("USER_BLOCKED", code(r))
        assertEquals(1, members(owner, id).size())
    }

    @Test
    fun `некорректные токены и параметры приглашения`() {
        val owner = newUser("inv_owner9")
        val id = createGroup(owner)["id"].asString()
        invalidRequests {
            assertEquals("INVALID_INVITATION", code(accept(owner, "")))
            assertEquals("INVALID_INVITATION", code(accept(owner, "x".repeat(500))))
            for (bad in listOf(
                mapOf("maxUses" to 0),
                mapOf("maxUses" to 101),
                mapOf("ttlHours" to 0),
                mapOf(
                    "ttlHours" to 721,
                ),
                mapOf("email" to "не-почта"),
            )) {
                val r = call(HttpMethod.POST, "/groups/$id/invitations", owner, bad)
                assertEquals(400, status(r), bad.toString())
                assertEquals("VALIDATION_FAILED", code(r))
            }
        }
    }

    @Test
    fun `конкурентное принятие одноразового кода - вступает только один`() {
        val owner = newUser("inv_owner10")
        val id = createGroup(owner)["id"].asString()
        val token = invite(owner, id)
        val users = (1..4).map { newUser("inv_race10_$it") }

        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(users.size)
        try {
            val barrier = java.util.concurrent.CyclicBarrier(users.size)
            val results =
                users
                    .map { u ->
                        pool.submit<Int> {
                            barrier.await()
                            status(accept(u, token))
                        }
                    }.map { it.get() }
            assertEquals(1, results.count { it == 200 }, "статусы: $results")
            assertTrue(results.filter { it != 200 }.all { it == 400 })
        } finally {
            pool.shutdownNow()
        }
        assertEquals(2, members(owner, id).size())
    }
}
