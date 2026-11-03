package app.podgon.mobile

import app.podgon.contract.ReviewDetails
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.network.ApiJson
import app.podgon.mobile.core.network.ReviewsApi
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.core.network.createPublicClient
import app.podgon.mobile.core.settings.AppLanguage
import app.podgon.mobile.core.settings.LanguageSettings
import app.podgon.mobile.core.settings.LanguageStore
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.core.share.reviewShareText
import app.podgon.mobile.feature.home.toDefinition
import app.podgon.mobile.feature.home.toFilter
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.feature.settings.SettingsViewModel
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.TestConfig
import app.podgon.mobile.testing.json
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Хранилище языка в памяти для тестов. */
class MemoryLanguageStore(
    var value: String? = null,
) : LanguageStore {
    override fun read(): String? = value

    override fun write(code: String?) {
        value = code
    }
}

/** Этап 8.3.1: язык приложения, поиск только по названиям. */
@OptIn(ExperimentalCoroutinesApi::class)
class Stage83SmallTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `выбранный язык сохраняется и уходит в профиль`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json("[]") }
            val store = MemoryLanguageStore()
            val languages = LanguageSettings(store)
            val vm =
                SettingsViewModel(
                    fixture.api,
                    SavedFiltersApi(fixture.client),
                    ThemeSettings(ResponseCache(fixture.cacheDao)),
                    languages,
                    fixture.me,
                )
            vm.setLanguage(AppLanguage.EN)
            assertEquals(AppLanguage.EN, vm.language.first { it == AppLanguage.EN })
            assertEquals("en", store.value)
            assertEquals("en", languages.effective)
            val patch = fixture.snapshot().single { it.method == HttpMethod.Patch }
            assertEquals("/api/v1/me", patch.url.encodedPath)
            assertTrue((patch.body as io.ktor.http.content.TextContent).text.contains("\"locale\":\"en\""))
            assertEquals(AppLanguage.EN, LanguageSettings(store).language.value, "выбор переживает перезапуск")
        }

    @Test
    fun `язык передаётся заголовком Accept-Language`() =
        runTest(dispatcher) {
            val seen = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { r ->
                    seen += r
                    json("[]")
                }
            ReviewsApi(createPublicClient(engine, TestConfig) { "en" }).myTags()
            assertEquals("en", seen.single().headers[HttpHeaders.AcceptLanguage])
            seen.clear()
            ReviewsApi(createPublicClient(engine, TestConfig)).myTags()
            assertNull(seen.single().headers[HttpHeaders.AcceptLanguage])
        }

    @Test
    fun `текст рекомендации для отправки`() {
        val review =
            ApiJson.decodeFromString<ReviewDetails>(
                """{"id":"r","title":"Дюна","description":"Про пустыню","rating":9,"categoryId":"c",
                "tags":["фантастика","книги"],"author":{"id":"u","username":"u","displayName":"У"},
                "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z","personal":true,
                "publications":[]}""",
            )
        assertEquals(
            "Дюна — 9/10\nКниги\nПро пустыню\n#фантастика #книги\n— Podgon",
            reviewShareText(review, "Книги", "Podgon"),
        )
        assertEquals(
            "Дюна — 9/10\n— Podgon",
            reviewShareText(review.copy(description = "", tags = emptyList()), null, "Podgon"),
        )
    }

    @Test
    fun `режим только по названиям уходит в запрос и сохраняется в фильтре`() =
        runTest(dispatcher) {
            val seen = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { r ->
                    seen += r
                    json("""{"items":[]}""")
                }
            val api = ReviewsApi(createPublicClient(engine, TestConfig))
            api.search(ReviewFilter(query = "кофе", titleOnly = true))
            assertEquals("true", seen.single().url.parameters["titleOnly"])
            seen.clear()
            api.search(ReviewFilter(titleOnly = true))
            assertNull(seen.single().url.parameters["titleOnly"], "без запроса режим не передаётся")
            val filter = ReviewFilter(titleOnly = true, minRating = 7)
            assertEquals(filter, filter.toDefinition().toFilter())
            assertEquals(2, filter.panelCount)
        }
}
