package app.podgon.identity

import app.podgon.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

class ProfileAndSessionsTest : IntegrationTest() {
    private fun patchMe(
        access: String,
        body: Map<String, Any?>,
    ): MvcResult =
        mockMvc
            .patch("/api/v1/me") {
                contentType = MediaType.APPLICATION_JSON
                content = json.writeValueAsString(body)
                header("Authorization", "Bearer $access")
            }.andReturn()

    private fun sessions(access: String): JsonNode =
        body(mockMvc.get("/api/v1/me/sessions") { header("Authorization", "Bearer $access") }.andReturn())

    private fun deleteSession(
        access: String,
        id: String,
    ): MvcResult = mockMvc.delete("/api/v1/me/sessions/$id") { header("Authorization", "Bearer $access") }.andReturn()

    @Test
    fun `обновление профиля - частичное, bio очищается пустой строкой`() {
        val access = registerUser("p1@example.com", "profile_one")["accessToken"].asString()
        val r = patchMe(access, mapOf("displayName" to "Новое имя", "bio" to "Люблю книги"))
        assertEquals(200, r.response.status)
        assertEquals("Новое имя", body(r)["displayName"].asString())
        assertEquals("Люблю книги", body(r)["bio"].asString())
        assertEquals("profile_one", body(r)["username"].asString())

        val cleared = patchMe(access, mapOf("bio" to ""))
        assertEquals("", body(cleared)["bio"].asString())
        assertEquals("Новое имя", body(cleared)["displayName"].asString())

        val locale = patchMe(access, mapOf("locale" to "en", "username" to "renamed.one"))
        assertEquals("en", body(locale)["locale"].asString())
        assertEquals("renamed.one", body(locale)["username"].asString())
    }

    @Test
    fun `смена username на занятый - 409, на некорректный - 400`() {
        registerUser("p2@example.com", "taken_name")
        val access = registerUser("p3@example.com", "free_name")["accessToken"].asString()
        val taken = patchMe(access, mapOf("username" to "TAKEN_NAME"))
        assertEquals(409, taken.response.status)
        assertEquals("USERNAME_TAKEN", body(taken)["code"].asString())

        val bad =
            invalidRequests { patchMe(access, mapOf("username" to "x", "bio" to "я".repeat(121), "locale" to "de")) }
        assertEquals(400, bad.response.status)
        val errors = body(bad)["errors"]
        assertEquals(
            setOf("username", "bio", "locale"),
            (0 until errors.size()).map { errors[it]["field"].asString() }.toSet(),
        )
    }

    @Test
    fun `список сессий отмечает текущую, завершённая сессия перестаёт обновляться`() {
        val mobile = registerUser("s1@example.com", "sessions_one", deviceInfo = "Pixel 8")
        val tablet = login("s1@example.com", deviceInfo = "Tablet")
        val access = mobile["accessToken"].asString()

        val list = sessions(access)
        assertEquals(2, list.size())
        val current = list.single { it["current"].asBoolean() }
        assertEquals("Pixel 8", current["deviceInfo"].asString())
        val other = list.single { !it["current"].asBoolean() }
        assertEquals("Tablet", other["deviceInfo"].asString())

        assertEquals(204, deleteSession(access, other["id"].asString()).response.status)
        assertEquals(1, sessions(access).size())
        assertEquals(
            401,
            post("/auth/refresh", mapOf("refreshToken" to tablet["refreshToken"].asString())).response.status,
        )
    }

    @Test
    fun `после обновления токена сессия остаётся одной`() {
        val tokens = registerUser("s2@example.com", "sessions_two")
        val refreshed = body(post("/auth/refresh", mapOf("refreshToken" to tokens["refreshToken"].asString())))
        val list = sessions(refreshed["accessToken"].asString())
        assertEquals(1, list.size())
        assertEquals(true, list[0]["current"].asBoolean())
    }

    @Test
    fun `чужую или несуществующую сессию завершить нельзя`() {
        val mine = registerUser("s3@example.com", "sessions_three")["accessToken"].asString()
        val theirs = registerUser("s4@example.com", "sessions_four")["accessToken"].asString()
        val theirSession = sessions(theirs)[0]["id"].asString()

        assertEquals(404, deleteSession(mine, theirSession).response.status)
        assertEquals(404, deleteSession(mine, "00000000-0000-0000-0000-000000000099").response.status)
        assertEquals(404, deleteSession(mine, "not-a-uuid").response.status)
        assertEquals(1, sessions(theirs).size())
    }

    @Test
    fun `профиль и сессии требуют авторизации`() {
        invalidRequests {
            for (path in listOf("/api/v1/me", "/api/v1/me/sessions")) {
                assertEquals(
                    401,
                    mockMvc
                        .get(path)
                        .andReturn()
                        .response.status,
                )
            }
            assertEquals(
                401,
                mockMvc
                    .delete("/api/v1/me/sessions/x")
                    .andReturn()
                    .response.status,
            )
        }
    }
}
