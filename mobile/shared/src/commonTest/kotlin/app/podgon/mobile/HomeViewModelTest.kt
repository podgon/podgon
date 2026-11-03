package app.podgon.mobile

import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.feature.home.HomeArea
import app.podgon.mobile.feature.home.HomeViewModel
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.groupJson
import app.podgon.mobile.testing.json
import io.ktor.client.request.HttpRequestData
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

private const val SET =
    """{"version":"v1","items":[
    {"id":"c1","key":"books","parentId":null,"name":"Книги","sortOrder":1,"system":false},
    {"id":"c2","key":"books.sf","parentId":"c1","name":"Фантастика","sortOrder":2,"system":false},
    {"id":"c3","key":"food","parentId":null,"name":"Еда","sortOrder":3,"system":false}]}"""

private const val TOC =
    """{"sections":[
    {"section":"category:c1","kind":"CATEGORY","pinned":false,"count":2,"lastSharedAt":"2026-06-01T10:00:00Z","categoryId":"c1"},
    {"section":"category:c3","kind":"CATEGORY","pinned":false,"count":1,"lastSharedAt":"2026-06-01T09:00:00Z","categoryId":"c3"}]}"""

private fun card(
    id: String,
    category: String = "c2",
) = """{"id":"$id","title":"$id","excerpt":"","rating":8,"tags":[],"author":{"id":"u","username":"u","displayName":"Ю"},
    "createdAt":"2026-06-01T10:00:00Z","categoryId":"$category","sharedAt":"2026-06-01T10:00:00Z","groupIds":["g1"]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                // заглушка кэша и список запросов пишутся с другого потока: при гонке проверка повторяется
                while (!runCatching(condition).getOrDefault(false)) delay(10)
            }
        }
    }

    private fun fixture(onFeed: (HttpRequestData) -> String = { """{"items":[${card("a")}]}""" }) =
        GroupsFixture { r ->
            val path = r.url.encodedPath
            when {
                path.endsWith("/categories") -> json(SET)
                path.endsWith("/feed") -> json(TOC)
                path == "/api/v1/groups" -> json("[${groupJson("g1", "Клуб")}]")
                path.endsWith(
                    "/facets",
                ) -> json("""{"categories":[],"tags":[{"name":"кофе","count":3}],"authors":[]}""")
                else -> json(onFeed(r))
            }
        }

    private fun vm(
        f: GroupsFixture,
        area: HomeArea = HomeArea.Feed,
    ) = HomeViewModel(
        area,
        f.reviewsRepository,
        f.reviews,
        f.api,
        SavedFiltersApi(f.client),
        f.categories,
        ResponseCache(f.cacheDao),
    )

    @Test
    fun `оглавление даёт секции а записи грузятся по секциям со своим параметром`() =
        runTest(dispatcher) {
            val f =
                fixture { r ->
                    if (r.url.parameters["section"] ==
                        "category:c3"
                    ) {
                        """{"items":[${card("b", "c3")}]}"""
                    } else {
                        """{"items":[${card("a")}]}"""
                    }
                }
            val home = vm(f)
            val state = home.state.first { s -> s.sections.size == 2 && s.sections.all { it.loaded } }
            assertEquals(listOf("a"), state.sections[0].items.map { it.id })
            assertEquals(listOf("b"), state.sections[1].items.map { it.id })
            assertEquals(
                "Фантастика",
                state.categoryLabel(state.sections[0].items[0], inCategorySection = true),
                "в секции — подкатегория",
            )
            assertEquals(
                null,
                state.categoryLabel(state.sections[1].items[0], inCategorySection = true),
                "корень уже в заголовке",
            )
            val sections = f.snapshot().mapNotNull { it.url.parameters["section"] }
            assertEquals(setOf("category:c1", "category:c3"), sections.toSet())
            assertEquals(listOf("Клуб"), state.groups.map { it.name })
        }

    @Test
    fun `сохранённый фильтр применяется в одно касание вместе с запросом`() =
        runTest(dispatcher) {
            val f =
                fixture { r ->
                    if (r.url.encodedPath.endsWith("/me/filters")) {
                        """[{"id":"f2","kind":"FILTER","inFeed":false,"name":"Б",
                        "definition":{"v":1,"q":"кофе","minRating":7,"titleOnly":true}},
                        {"id":"c","kind":"CATEGORY","inFeed":true,"categoryId":"c1"},
                        {"id":"f1","kind":"FILTER","inFeed":false,"name":"А","definition":{"v":1}}]"""
                    } else {
                        """{"items":[${card("a")}]}"""
                    }
                }
            val home = vm(f)
            home.state.first { s -> s.sections.isNotEmpty() }
            home.loadSavedFilters()
            val saved = home.state.first { it.savedFilters.isNotEmpty() }.savedFilters
            assertEquals(listOf("А", "Б"), saved.map { it.name }, "только фильтры, по алфавиту")

            home.applySaved(saved[1])
            val state = home.state.first { it.query == "кофе" && it.filter.minRating == 7 }
            assertTrue(state.filter.titleOnly)
            awaitUntil {
                f.snapshot().any {
                    it.url.parameters["q"] == "кофе" &&
                        it.url.parameters["minRating"] == "7" &&
                        it.url.parameters["titleOnly"] == "true"
                }
            }
        }

    @Test
    fun `свёрнутая секция запоминается и не грузится при следующем открытии`() =
        runTest(dispatcher) {
            val f = fixture()
            val first = vm(f)
            first.state.first { s -> s.sections.isNotEmpty() && s.sections.all { it.loaded } }
            first.toggle("category:c3")
            awaitUntil {
                f.cacheDao.entries.keys
                    .any { it.startsWith("collapsed:") }
            }

            f.requests.clear()
            val second = vm(f)
            val state = second.state.first { s -> s.sections.size == 2 && s.sections[0].loaded }
            assertEquals(false, state.sections[1].expanded)
            assertTrue(f.snapshot().none { it.url.parameters["section"] == "category:c3" }, "свёрнутая не грузится")
        }

    @Test
    fun `поиск показывает плоский список и запоминает запрос`() =
        runTest(dispatcher) {
            val f = fixture()
            val home = vm(f)
            home.state.first { !it.loading && it.sections.isNotEmpty() }
            home.search("  дюна ")
            val state = home.state.first { it.searching && it.results.isNotEmpty() }
            assertEquals(listOf("a"), state.results.map { it.id })
            assertEquals(listOf("дюна"), state.recent)
            assertEquals(
                "дюна",
                f
                    .snapshot()
                    .last { it.url.encodedPath == "/api/v1/reviews" }
                    .url.parameters["q"],
            )
            home.loadSuggestions()
            assertEquals(
                listOf("кофе"),
                home.state
                    .first { it.popularTags.isNotEmpty() }
                    .popularTags
                    .map { it.name },
            )
        }

    @Test
    fun `фильтр запоминается для вкладки и восстанавливается при возврате`() =
        runTest(dispatcher) {
            val f = fixture()
            val home = vm(f)
            home.state.first { !it.loading && it.groups.isNotEmpty() }
            home.setFilter(ReviewFilter(minRating = 8))
            awaitUntil { "filter:all" in f.cacheDao.entries }
            awaitUntil {
                f.snapshot().any {
                    it.url.encodedPath.endsWith("/feed") &&
                        it.url.parameters["minRating"] == "8"
                }
            }

            home.selectTab("g1")
            assertEquals(
                ReviewFilter(),
                home.state.first { it.tab == "g1" && !it.loading }.filter,
                "у группы свой фильтр",
            )
            home.selectTab(null)
            assertEquals(
                8,
                home.state
                    .first { it.tab == null && !it.loading }
                    .filter.minRating,
            )
        }

    @Test
    fun `лента по тегу не перезаписывает фильтр вкладки`() =
        runTest(dispatcher) {
            val f = fixture()
            val home = vm(f, HomeArea.Tagged("кофе"))
            val state = home.state.first { !it.loading && it.sections.isNotEmpty() }
            assertEquals(setOf("кофе"), state.filter.tags)
            home.setFilter(ReviewFilter(tags = setOf("кофе"), minRating = 5))
            home.state.first { it.filter.minRating == 5 && !it.loading }
            assertTrue("filter:all" !in f.cacheDao.entries)
        }
}
