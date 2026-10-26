package app.podgon.groups

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

@TestPropertySource(
    properties = [
        "podgon.groups.max-groups-per-user=2",
        "podgon.groups.max-members-per-group=2",
        "podgon.groups.max-roles-per-group=3",
        "podgon.groups.max-active-invitations-per-group=2",
        "podgon.groups.max-email-invitations-per-user-per-hour=2",
    ],
)
class GroupLimitsTest : GroupsTestBase() {
    @Test
    fun `лимит групп на пользователя`() {
        val owner = newUser("lim_owner1")
        createGroup(owner, "Первая")
        createGroup(owner, "Вторая")
        val r = call(HttpMethod.POST, "/groups", owner, mapOf("name" to "Третья"))
        assertEquals(409, status(r))
        assertEquals("LIMIT_EXCEEDED", code(r))
    }

    @Test
    fun `лимит участников и ролей`() {
        val owner = newUser("lim_owner2")
        val id = createGroup(owner)["id"].asString()
        join(owner, id, newUser("lim_member2"))
        val full = accept(newUser("lim_late2"), invite(owner, id))
        assertEquals(409, status(full))
        assertEquals("LIMIT_EXCEEDED", code(full))

        assertEquals(201, status(call(HttpMethod.POST, "/groups/$id/roles", owner, mapOf("title" to "Одна"))))
        assertEquals(409, status(call(HttpMethod.POST, "/groups/$id/roles", owner, mapOf("title" to "Вторая"))))
    }

    @Test
    fun `лимит действующих приглашений и писем в час`() {
        val owner = newUser("lim_owner3")
        val id = createGroup(owner)["id"].asString()
        invite(owner, id)
        invite(owner, id)
        val r = call(HttpMethod.POST, "/groups/$id/invitations", owner, mapOf("maxUses" to 1))
        assertEquals(409, status(r))
        assertEquals("LIMIT_EXCEEDED", code(r))

        val other = newUser("lim_owner3b")
        val otherGroup = createGroup(other)["id"].asString()
        invite(other, otherGroup, email = "a@example.com")
        invite(other, otherGroup, email = "b@example.com")
        // третье письмо за час: сначала освобождаем место под действующие приглашения
        val ids = body(get("/groups/$otherGroup/invitations", other)).toList().map { it["id"].asString() }
        ids.forEach { delete("/groups/$otherGroup/invitations/$it", other) }
        val limited = call(HttpMethod.POST, "/groups/$otherGroup/invitations", other, mapOf("email" to "c@example.com"))
        assertEquals(429, status(limited))
        assertEquals("RATE_LIMITED", code(limited))
        assertEquals("3600", limited.response.getHeader("Retry-After"))
    }
}
