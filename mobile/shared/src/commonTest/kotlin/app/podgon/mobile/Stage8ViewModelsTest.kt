package app.podgon.mobile

import app.cash.turbine.test
import app.podgon.contract.ShareMode
import app.podgon.mobile.core.auth.StoredTokens
import app.podgon.mobile.core.media.MyProfile
import app.podgon.mobile.feature.groups.GroupEffect
import app.podgon.mobile.feature.groups.GroupViewModel
import app.podgon.mobile.feature.groups.JoinGroupViewModel
import app.podgon.mobile.feature.profile.ProfileViewModel
import app.podgon.mobile.testing.ADMIN_ROLE
import app.podgon.mobile.testing.AuthFixture
import app.podgon.mobile.testing.FakeTokenStorage
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.MEMBER_ROLE
import app.podgon.mobile.testing.ME_JSON
import app.podgon.mobile.testing.groupJson
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.memberJson
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val INVITE_PREVIEW = """{"groupName":"Бегуны","groupDescription":"","memberCount":3,"invitedBy":"Аня"}"""
private const val SHARE_PREVIEW = """{"total":3,"items":[{"reviewId":"r1","title":"Дюна","categoryId":"sc1"}]}"""
private const val CATEGORY_SET =
    """{"version":"v1","items":[
    {"id":"sc1","key":"film","parentId":null,"name":"Фильмы","sortOrder":1,"system":false},
    {"id":"sc2","key":"film.movies","parentId":"sc1","name":"Кино","sortOrder":2,"system":false},
    {"id":"none","key":"none","parentId":null,"name":"Без категории","sortOrder":3,"system":true}]}"""

private fun HttpRequestData.text(): String = (body as TextContent).text

@OptIn(ExperimentalCoroutinesApi::class)
class Stage8ViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (!condition()) delay(10)
            }
        }
    }

    private fun joinFixture() =
        GroupsFixture { request ->
            when (request.url.encodedPath) {
                "/api/v1/invitations/preview" -> json(INVITE_PREVIEW)
                "/api/v1/invitations/share-preview" -> json(SHARE_PREVIEW)
                "/api/v1/groups" -> json("[${groupJson("g1", "Мой клуб")}]")
                "/api/v1/categories" -> json(CATEGORY_SET)
                else -> json(groupJson("g7", "Бегуны", MEMBER_ROLE, 4))
            }
        }

    @Test
    fun `вступление с правилом все - предпросмотр и отправка правила`() =
        runTest(dispatcher) {
            val fixture = joinFixture()
            val vm = JoinGroupViewModel(fixture.api, fixture.categories)
            vm.onTokenChange("abc")
            vm.check()
            vm.state.first { it.preview != null }

            vm.setMode(ShareMode.ALL)
            val shown = vm.state.first { it.sharePreview != null }
            assertEquals(3, shown.sharePreview?.total)
            assertEquals(listOf("Дюна"), shown.sharePreview?.items?.map { it.title })

            vm.joined.test {
                vm.confirm()
                assertEquals("g7", awaitItem())
            }
            val accept = fixture.requests.last { it.url.encodedPath == "/api/v1/invitations/accept" }.text()
            assertTrue("\"mode\":\"ALL\"" in accept, accept)
            assertTrue("assignments" !in accept, accept)
        }

    @Test
    fun `без правила вступление идёт без share`() =
        runTest(dispatcher) {
            val fixture = joinFixture()
            val vm = JoinGroupViewModel(fixture.api, fixture.categories)
            vm.onTokenChange("abc")
            vm.check()
            vm.state.first { it.preview != null }
            vm.joined.test {
                vm.confirm()
                awaitItem()
            }
            val accept = fixture.requests.last { it.url.encodedPath == "/api/v1/invitations/accept" }.text()
            assertTrue("share" !in accept, accept)
            assertTrue(fixture.calls().none { it.endsWith("share-preview") })
        }

    @Test
    fun `правило по категориям не запрашивает предпросмотр пока ничего не выбрано`() =
        runTest(dispatcher) {
            val fixture = joinFixture()
            val vm = JoinGroupViewModel(fixture.api, fixture.categories)
            vm.onTokenChange("abc")
            vm.check()
            vm.state.first { it.preview != null }

            vm.setMode(ShareMode.CATEGORIES)
            val loaded = vm.state.first { it.categories.isNotEmpty() }
            assertEquals(listOf("Фильмы", "Кино"), loaded.categories.map { it.category.name }, "без «Без категории»")
            assertNull(loaded.rule)
            assertTrue(fixture.calls().none { it.endsWith("share-preview") })

            vm.toggleCategory("sc1")
            vm.state.first { it.sharePreview != null }
            val request = fixture.requests.last { it.url.encodedPath.endsWith("share-preview") }.text()
            assertTrue("\"sc1\"" in request && "CATEGORIES" in request, request)
        }

    @Test
    fun `теги разбираются и предпросмотр запрашивается явно`() =
        runTest(dispatcher) {
            val fixture = joinFixture()
            val vm = JoinGroupViewModel(fixture.api, fixture.categories)
            vm.onTokenChange("abc")
            vm.check()
            vm.state.first { it.preview != null }
            vm.setMode(ShareMode.TAGS_EXCEPT)
            vm.onTagsChange("#SciFi, драма  bad!tag")
            assertEquals(listOf("scifi", "драма"), vm.state.value.tags)
            assertTrue(fixture.calls().none { it.endsWith("share-preview") })
            vm.refreshSharePreview()
            vm.state.first { it.sharePreview != null }
            assertTrue("TAGS_EXCEPT" in fixture.requests.last { it.url.encodedPath.endsWith("share-preview") }.text())
        }

    private fun groupFixture() =
        GroupsFixture { request ->
            when (request.url.encodedPath) {
                "/api/v1/groups/g1" -> json(groupJson("g1"))
                "/api/v1/groups/g1/members" -> json("[${memberJson("u-me", "me", ADMIN_ROLE)}]")
                "/api/v1/groups/g1/roles" -> json("[$ADMIN_ROLE,$MEMBER_ROLE]")
                "/api/v1/me" -> json(ME_JSON)
                "/api/v1/groups/g1/leave-impact" -> json("""{"myPublications":4}""")
                "/api/v1/groups/g1/delete-impact" -> json("""{"exclusiveReviews":2,"publications":5}""")
                else -> respond("", HttpStatusCode.NoContent)
            }
        }

    @Test
    fun `выход - сначала последствия затем флаг удаления публикаций`() =
        runTest(dispatcher) {
            val fixture = groupFixture()
            val vm = GroupViewModel("g1", fixture.api, fixture.me)
            vm.state.first { !it.loading }
            vm.prepareLeave()
            assertEquals(
                4,
                vm.state
                    .first { it.leaveImpact != null }
                    .leaveImpact
                    ?.myPublications,
            )
            vm.effects.test {
                vm.leave(deleteMyReviews = true)
                assertEquals(GroupEffect.Closed, awaitItem())
            }
            assertTrue(
                fixture.requests.any {
                    it.url.encodedPath.endsWith("/leave") &&
                        "deleteMyReviews=true" in it.url.toString()
                },
            )
        }

    @Test
    fun `удаление группы передаёт судьбу рекомендаций`() =
        runTest(dispatcher) {
            val fixture = groupFixture()
            val vm = GroupViewModel("g1", fixture.api, fixture.me)
            vm.state.first { !it.loading }
            vm.prepareDelete()
            assertEquals(
                2,
                vm.state
                    .first { it.deleteImpact != null }
                    .deleteImpact
                    ?.exclusiveReviews,
            )
            vm.dismissImpact()
            assertNull(vm.state.value.deleteImpact)
            vm.effects.test {
                vm.deleteGroup(app.podgon.contract.ReviewsFate.DELETE)
                assertEquals(GroupEffect.Closed, awaitItem())
            }
            val url =
                fixture.requests
                    .last { it.method.value == "DELETE" }
                    .url
                    .toString()
            assertTrue("reviews=DELETE" in url && "confirm=true" in url, url)
        }

    @Test
    fun `аватар загружается и убирается а верхняя строка узнаёт о нём`() =
        runTest(dispatcher) {
            val storage = FakeTokenStorage(StoredTokens("a", "r"))
            val auth = AuthFixture(storage) { json("{}") }
            val withAvatar = ME_JSON.replace("\"locale\":\"ru\",", "\"locale\":\"ru\",\"avatarId\":\"a1\",")
            val fixture =
                GroupsFixture { request ->
                    when {
                        request.url.encodedPath == "/api/v1/me/avatar" && request.method == HttpMethod.Put ->
                            json(
                                withAvatar,
                            )
                        request.url.encodedPath == "/api/v1/me/sessions" -> json("[]")
                        else -> json(ME_JSON)
                    }
                }
            val profile = MyProfile()
            val vm = ProfileViewModel(fixture.me, auth.repository, profile)
            vm.state.first { !it.loading }
            vm.setAvatar(byteArrayOf(9))
            assertEquals(
                "a1",
                vm.state
                    .first { it.profile?.avatarId != null }
                    .profile
                    ?.avatarId,
            )
            assertEquals("a1", profile.profile.value?.avatarId)
            vm.setAvatar(null)
            vm.state.first { it.profile?.avatarId == null && !it.avatarBusy }
            assertTrue(fixture.calls().contains("DELETE /api/v1/me/avatar"))
        }

    private fun profileSetup(blocking: String = "[]"): Triple<ProfileViewModel, GroupsFixture, FakeTokenStorage> {
        val storage = FakeTokenStorage(StoredTokens("a", "r"))
        val auth = AuthFixture(storage) { json("{}") }
        val fixture =
            GroupsFixture { request ->
                when (request.url.encodedPath) {
                    "/api/v1/me" -> json(ME_JSON)
                    "/api/v1/me/sessions" -> json("[]")
                    "/api/v1/me/delete-impact" ->
                        json("""{"reviews":5,"publishedReviews":3,"blockingGroups":$blocking}""")
                    else -> respond("", HttpStatusCode.NoContent)
                }
            }
        return Triple(ProfileViewModel(fixture.me, auth.repository), fixture, storage)
    }

    @Test
    fun `удаление аккаунта - код подтверждает и закрывает локальную сессию`() =
        runTest(dispatcher) {
            val (vm, fixture, storage) = profileSetup()
            vm.state.first { !it.loading }
            vm.startDeletion()
            val opened = vm.state.first { it.deletion != null }.deletion!!
            assertEquals(5, opened.impact.reviews)
            assertTrue(!opened.canConfirm)

            vm.setDeleteReviews(true)
            vm.requestCode()
            vm.state.first { it.deletion?.codeSent == true }
            vm.onCodeChange("12ab3456789")
            assertEquals(
                "123456",
                vm.state.value.deletion
                    ?.code,
            )
            vm.confirmDeletion()
            awaitUntil { storage.tokens == null }
            val body = fixture.requests.last { it.url.encodedPath == "/api/v1/me/deletion" }.text()
            assertTrue("123456" in body && "true" in body, body)
        }

    @Test
    fun `группы единственного админа блокируют удаление`() =
        runTest(dispatcher) {
            val (vm, fixture, storage) = profileSetup("""[{"id":"g1","name":"Клуб"}]""")
            vm.state.first { !it.loading }
            vm.startDeletion()
            val opened = vm.state.first { it.deletion != null }.deletion!!
            assertTrue(opened.blocked)
            vm.requestCode()
            vm.onCodeChange("123456")
            vm.confirmDeletion()
            awaitUntil { true }
            assertTrue(fixture.calls().none { it.endsWith("/deletion") || it.endsWith("/deletion/code") })
            assertTrue(storage.tokens != null)
            vm.cancelDeletion()
            assertNull(vm.state.value.deletion)
        }
}
