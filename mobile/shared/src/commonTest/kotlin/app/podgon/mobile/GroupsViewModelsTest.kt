package app.podgon.mobile

import app.cash.turbine.test
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.feature.groups.CreateGroupViewModel
import app.podgon.mobile.feature.groups.GroupEffect
import app.podgon.mobile.feature.groups.GroupViewModel
import app.podgon.mobile.feature.groups.GroupsViewModel
import app.podgon.mobile.feature.groups.InvitationsViewModel
import app.podgon.mobile.feature.groups.JoinGroupViewModel
import app.podgon.mobile.feature.groups.RolesViewModel
import app.podgon.mobile.testing.ADMIN_ROLE
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.MEMBER_ROLE
import app.podgon.mobile.testing.ME_JSON
import app.podgon.mobile.testing.groupJson
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.memberJson
import app.podgon.mobile.testing.problem
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Ждёт условие в реальном времени (MockEngine отвечает на своём потоке), не дольше пяти секунд. */
    private suspend fun awaitUntil(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (!condition()) delay(10)
            }
        }
    }

    @Test
    fun `список групп загружается и ошибка сети остаётся видимой и не стирает прежний список`() =
        runTest(dispatcher) {
            var fail = false
            val fixture =
                GroupsFixture {
                    if (fail) {
                        error(
                            "нет сети",
                        )
                    } else {
                        json("[${groupJson("g1")},${groupJson("g2", "Бегуны", MEMBER_ROLE)}]")
                    }
                }
            val vm = GroupsViewModel(fixture.api)
            vm.load()
            val loaded = vm.state.first { !it.loading && it.groups.isNotEmpty() }
            assertEquals(listOf("Книжный клуб", "Бегуны"), loaded.groups.map { it.name })
            assertEquals(false, loaded.groups[1].myRole.admin)

            fail = true
            vm.load()
            val failed = vm.state.first { it.error != null }
            assertEquals(AppError.Network, failed.error)
            assertEquals(2, failed.groups.size)
        }

    @Test
    fun `создание группы - пустое название не уходит на сервер и успех отдаёт идентификатор`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json(groupJson("g-new", members = 1), HttpStatusCode.Created) }
            val vm = CreateGroupViewModel(fixture.api)
            vm.onNameChange("   ")
            vm.submit()
            assertEquals(FieldErrorReason.REQUIRED, vm.state.value.nameError)
            assertEquals(0, fixture.requests.size)

            vm.onNameChange("Книжный клуб")
            vm.created.test {
                vm.submit()
                assertEquals("g-new", awaitItem())
            }
            assertEquals(listOf("POST /api/v1/groups"), fixture.calls())
        }

    @Test
    fun `вступление по коду - предпросмотр и затем подтверждение`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/invitations/preview" ->
                            json("""{"groupName":"Бегуны","groupDescription":"","memberCount":3,"invitedBy":"Аня"}""")
                        else -> json(groupJson("g7", "Бегуны", MEMBER_ROLE, 4))
                    }
                }
            val vm = JoinGroupViewModel(fixture.api)
            vm.onTokenChange("  abc123 ")
            vm.check()
            val checked = vm.state.first { it.preview != null }
            assertEquals("Бегуны", checked.preview?.groupName)
            assertEquals("abc123", checked.token)

            vm.joined.test {
                vm.confirm()
                assertEquals("g7", awaitItem())
            }
            assertEquals(listOf("POST /api/v1/invitations/preview", "POST /api/v1/invitations/accept"), fixture.calls())
        }

    @Test
    fun `неверный код приглашения - ошибка и предпросмотра нет и вступить нельзя`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json(problem(400, "INVALID_INVITATION"), HttpStatusCode.BadRequest) }
            val vm = JoinGroupViewModel(fixture.api)
            vm.onTokenChange("bad")
            vm.check()
            val failed = vm.state.first { it.error != null }
            assertEquals(ErrorCode.INVALID_INVITATION, (failed.error as AppError.Api).code)
            assertNull(failed.preview)
            vm.confirm()
            assertEquals(1, fixture.requests.size, "без предпросмотра подтверждение не отправляется")
        }

    private fun groupHandler(
        extra: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) ->
        io.ktor.client.request.HttpResponseData? = { null },
    ) = GroupsFixture { request ->
        extra(request) ?: when (request.url.encodedPath) {
            "/api/v1/groups/g1" -> json(groupJson("g1"))
            "/api/v1/groups/g1/members" ->
                json(
                    "[${memberJson("u-me", "me", ADMIN_ROLE)},${memberJson("u-2", "anna")}]",
                )
            "/api/v1/groups/g1/roles" -> json("[$ADMIN_ROLE,$MEMBER_ROLE]")
            "/api/v1/me" -> json(ME_JSON)
            else -> respond("", HttpStatusCode.NoContent)
        }
    }

    @Test
    fun `экран группы собирает группу и участников и роли и свой идентификатор`() =
        runTest(dispatcher) {
            val fixture = groupHandler()
            val vm = GroupViewModel("g1", fixture.api, fixture.me)
            val state = vm.state.first { !it.loading }
            assertEquals("Книжный клуб", state.group?.name)
            assertEquals(2, state.members.size)
            assertEquals(2, state.roles.size)
            assertEquals("u-me", state.myUserId)
            assertTrue(state.isAdmin)
        }

    @Test
    fun `последнего администратора понизить нельзя - ошибка показана и список не меняется`() =
        runTest(dispatcher) {
            val fixture =
                groupHandler { request ->
                    if (request.method ==
                        HttpMethod.Put
                    ) {
                        json(problem(409, "LAST_ADMIN"), HttpStatusCode.Conflict)
                    } else {
                        null
                    }
                }
            val vm = GroupViewModel("g1", fixture.api, fixture.me)
            vm.state.first { !it.loading }
            vm.setRole("u-me", "r-member")
            val failed = vm.state.first { it.error != null }
            assertEquals(ErrorCode.LAST_ADMIN, (failed.error as AppError.Api).code)
            assertEquals(2, failed.members.size)
        }

    @Test
    fun `удаление участника перечитывает состав и выход закрывает экран`() =
        runTest(dispatcher) {
            val fixture = groupHandler()
            val vm = GroupViewModel("g1", fixture.api, fixture.me)
            vm.state.first { !it.loading }
            vm.removeMember("u-2")
            while (fixture.calls().count { it == "GET /api/v1/groups/g1/members" } <
                2
            ) {
                vm.state.first { true }.also { kotlinx.coroutines.yield() }
            }
            assertTrue("DELETE /api/v1/groups/g1/members/u-2" in fixture.calls())

            vm.effects.test {
                vm.leave()
                assertEquals(GroupEffect.Closed, awaitItem())
            }
            assertTrue("POST /api/v1/groups/g1/leave" in fixture.calls())
        }

    @Test
    fun `роли - пустое название не отправляется и создание обновляет список`() =
        runTest(dispatcher) {
            val created =
                """{"id":"r-new","code":"R1","title":"Модератор","description":"","admin":true,"system":false}"""
            val fixture =
                groupHandler { request ->
                    if (request.method == HttpMethod.Post) json(created, HttpStatusCode.Created) else null
                }
            val vm = RolesViewModel("g1", fixture.api)
            vm.state.first { !it.loading }
            var done = false
            vm.create("  ", "", false) { done = true }
            assertEquals(FieldErrorReason.REQUIRED, vm.state.value.titleError)
            assertEquals(false, done)
            assertEquals(1, fixture.requests.size, "только начальная загрузка ролей")

            vm.create("Модератор", "Следит", true) { done = true }
            awaitUntil { done }
            assertTrue("POST /api/v1/groups/g1/roles" in fixture.calls())
        }

    @Test
    fun `приглашения - некорректный email не отправляется и созданный токен показывается`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { request ->
                    if (request.method == HttpMethod.Post) {
                        json(
                            """{"id":"i1","token":"tok-123","email":null,"expiresAt":"2026-06-08T10:00:00Z","maxUses":1}""",
                            HttpStatusCode.Created,
                        )
                    } else {
                        json("[]")
                    }
                }
            val vm = InvitationsViewModel("g1", fixture.api)
            vm.state.first { !it.loading }
            vm.create("не-почта", 1)
            assertEquals(FieldErrorReason.INVALID_FORMAT, vm.state.value.emailError)
            assertEquals(1, fixture.requests.size)

            vm.create("", 5)
            val created = vm.state.first { it.created != null }
            assertEquals("tok-123", created.created?.token)
            vm.dismissCreated()
            assertNull(vm.state.value.created)
        }
}
