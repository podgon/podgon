package app.podgon.mobile

import app.cash.turbine.test
import app.podgon.contract.SavedFilter
import app.podgon.contract.SavedFilterKind
import app.podgon.mobile.core.network.NotificationsApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.feature.groups.GroupsViewModel
import app.podgon.mobile.feature.groups.JoinGroupViewModel
import app.podgon.mobile.feature.home.FiltersViewModel
import app.podgon.mobile.feature.notifications.NotificationsViewModel
import app.podgon.mobile.feature.notifications.UnreadCounter
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.MEMBER_ROLE
import app.podgon.mobile.testing.groupJson
import app.podgon.mobile.testing.json
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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
import kotlin.test.assertTrue

private const val PAGE =
    """{"unread":2,"items":[
    {"id":"n1","kind":"INVITATION","createdAt":"2026-11-01T10:00:00Z","read":false,"invitationId":"i1","groupName":"Бегуны","invitedBy":"Аня"},
    {"id":"n2","kind":"FILTER_MATCH","createdAt":"2026-11-01T09:00:00Z","read":false,"reviewId":"r1","reviewTitle":"Дюна","filterName":"Книги"},
    {"id":"n3","kind":"FILTER_MATCH","createdAt":"2026-10-01T09:00:00Z","read":true,"reviewId":"r2","reviewTitle":"Старое","filterName":"Книги"}]}"""

/** Этап 8.3.5: уведомления, подписки на фильтры, входящие приглашения. */
@OptIn(ExperimentalCoroutinesApi::class)
class Stage835NotificationsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (!runCatching(condition).getOrDefault(false)) delay(10) }
        }
    }

    @Test
    fun `при открытии уведомления отмечаются прочитанными а новые выделены`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when (r.url.encodedPath) {
                        "/api/v1/me/notifications/read" -> respond("", HttpStatusCode.NoContent)
                        "/api/v1/me/notifications/unread" -> json("""{"count":2}""")
                        else -> json(PAGE)
                    }
                }
            val api = NotificationsApi(fixture.client)
            val counter = UnreadCounter(api)
            counter.refresh()
            assertEquals(2, counter.count.value)
            val vm = NotificationsViewModel(api, counter)
            val state = vm.state.first { !it.loading }
            assertEquals(3, state.items.size)
            assertEquals(setOf("n1", "n2"), state.fresh)
            awaitUntil { counter.count.value == 0 }
            val read = fixture.snapshot().single { it.url.encodedPath.endsWith("/read") }
            assertEquals("{}", (read.body as TextContent).text, "все уведомления")
        }

    @Test
    fun `входящее приглашение принимается без кода`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when (r.url.encodedPath) {
                        "/api/v1/invitations/preview" ->
                            json("""{"groupName":"Бегуны","groupDescription":"","memberCount":3,"invitedBy":"Аня"}""")
                        else -> json(groupJson("g7", "Бегуны", MEMBER_ROLE, 4))
                    }
                }
            val vm = JoinGroupViewModel(fixture.api, fixture.categories)
            vm.openIncoming("i1")
            assertEquals(
                "Бегуны",
                vm.state
                    .first { it.preview != null }
                    .preview
                    ?.groupName,
            )
            vm.joined.test {
                vm.confirm()
                assertEquals("g7", awaitItem())
            }
            val bodies =
                fixture
                    .snapshot()
                    .filter {
                        it.method == HttpMethod.Post
                    }.map { (it.body as TextContent).text }
            assertTrue(bodies.all { "\"invitationId\":\"i1\"" in it && "token" !in it }, bodies.toString())
        }

    @Test
    fun `в группах видны входящие приглашения`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when (r.url.encodedPath) {
                        "/api/v1/me/invitations" ->
                            json(
                                """[{"id":"i1","groupName":"Бегуны","groupDescription":"","memberCount":3,
                                "invitedBy":"Аня","expiresAt":"2026-12-01T00:00:00Z"}]""",
                            )
                        else -> json("[]")
                    }
                }
            val vm = GroupsViewModel(fixture.api)
            vm.load()
            assertEquals(
                listOf("Бегуны"),
                vm.state
                    .first { it.incoming.isNotEmpty() }
                    .incoming
                    .map { it.groupName },
            )
        }

    @Test
    fun `колокольчик у сохранённого фильтра включает подписку`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.method == HttpMethod.Patch ->
                            json(
                                """{"id":"f1","kind":"FILTER","inFeed":false,"name":"А","subscribed":true}""",
                            )
                        r.url.encodedPath.endsWith("/me/filters") -> json("[]")
                        r.url.encodedPath.endsWith("/categories") -> json("""{"version":"v","items":[]}""")
                        else -> json("""{"categories":[],"tags":[],"authors":[]}""")
                    }
                }
            val vm =
                FiltersViewModel(
                    null,
                    ReviewFilter(),
                    fixture.reviews,
                    SavedFiltersApi(fixture.client),
                    fixture.categories,
                )
            vm.setSubscribed(SavedFilter("f1", SavedFilterKind.FILTER, inFeed = false, name = "А"), true)
            awaitUntil { fixture.snapshot().any { it.method == HttpMethod.Patch } }
            val patch = fixture.snapshot().single { it.method == HttpMethod.Patch }
            assertEquals("/api/v1/me/filters/f1", patch.url.encodedPath)
            assertTrue("\"subscribed\":true" in (patch.body as TextContent).text)
        }
}
