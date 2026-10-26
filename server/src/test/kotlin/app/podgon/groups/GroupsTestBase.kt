package app.podgon.groups

import app.podgon.IntegrationTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode

/** Общие помощники тестов групп: пользователи, вызовы API с токеном, типичные сценарии. */
abstract class GroupsTestBase : IntegrationTest() {
    /** Регистрирует пользователя и возвращает его access-токен. */
    fun newUser(name: String): String = registerUser("$name@example.com", name)["accessToken"].asString()

    fun call(
        method: HttpMethod,
        path: String,
        access: String?,
        body: Any? = null,
    ): MvcResult =
        mockMvc
            .perform(
                request(method, "/api/v1$path").apply {
                    access?.let { header("Authorization", "Bearer $it") }
                    if (body != null) {
                        contentType(MediaType.APPLICATION_JSON)
                        content(json.writeValueAsString(body))
                    }
                },
            ).andReturn()

    fun get(
        path: String,
        access: String?,
    ) = call(HttpMethod.GET, path, access)

    fun delete(
        path: String,
        access: String?,
    ) = call(HttpMethod.DELETE, path, access)

    fun status(result: MvcResult) = result.response.status

    fun code(result: MvcResult): String = body(result)["code"].asString()

    fun userId(access: String): String = body(get("/me", access))["id"].asString()

    fun createGroup(
        access: String,
        name: String = "Книжный клуб",
    ): JsonNode {
        val r = call(HttpMethod.POST, "/groups", access, mapOf("name" to name, "description" to "Читаем вместе"))
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)
    }

    /** Приглашение по коду; возвращает токен. */
    fun invite(
        access: String,
        groupId: String,
        email: String? = null,
        maxUses: Int = 1,
    ): String {
        val r =
            call(
                HttpMethod.POST,
                "/groups/$groupId/invitations",
                access,
                mapOf(
                    "email" to email,
                    "maxUses" to maxUses,
                ),
            )
        check(status(r) == 201) { r.response.contentAsString }
        return body(r)["token"].asString()
    }

    fun accept(
        access: String,
        token: String,
    ) = call(HttpMethod.POST, "/invitations/accept", access, mapOf("token" to token))

    /** Пользователь вступает в группу по свежему приглашению. */
    fun join(
        adminAccess: String,
        groupId: String,
        memberAccess: String,
    ) {
        val r = accept(memberAccess, invite(adminAccess, groupId))
        check(status(r) == 200) { r.response.contentAsString }
    }

    fun members(
        access: String,
        groupId: String,
    ): JsonNode = body(get("/groups/$groupId/members", access))

    fun roleId(
        access: String,
        groupId: String,
        code: String,
    ): String = body(get("/groups/$groupId/roles", access)).first { it["code"].asString() == code }["id"].asString()

    fun setRole(
        access: String,
        groupId: String,
        userId: String,
        roleId: String,
    ) = call(HttpMethod.PUT, "/groups/$groupId/members/$userId/role", access, mapOf("roleId" to roleId))
}
