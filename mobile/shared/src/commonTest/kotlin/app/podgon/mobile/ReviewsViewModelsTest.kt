package app.podgon.mobile

import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.cache.CachedResponse
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.feature.reviews.FeedScope
import app.podgon.mobile.feature.reviews.FeedViewModel
import app.podgon.mobile.feature.reviews.ReviewFilter
import app.podgon.mobile.feature.reviews.ReviewFormEffect
import app.podgon.mobile.feature.reviews.ReviewFormViewModel
import app.podgon.mobile.feature.reviews.ReviewViewModel
import app.podgon.mobile.feature.reviews.parseTags
import app.podgon.mobile.testing.ADMIN_ROLE
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.ME_JSON
import app.podgon.mobile.testing.groupJson
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.memberJson
import app.podgon.mobile.testing.problem
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

private const val AUTHOR = """{"id":"u-me","username":"me","displayName":"Я"}"""

private fun card(
    id: String,
    title: String = id,
) =
    """{"id":"$id","title":"$title","excerpt":"","rating":8,"tags":[],"author":$AUTHOR,"createdAt":"2026-06-01T10:00:00Z",
    "categoryId":"c1"}"""

private fun details(
    id: String = "r1",
    author: String = AUTHOR,
    publications: String = """[{"groupId":"g1","sharedAt":"2026-06-01T10:00:00Z"}]""",
    categoryId: String = "c1",
) = """{"id":"$id","title":"Дюна","description":"Книга","rating":9,"categoryId":"$categoryId","tags":["scifi"],
    "author":$author,"createdAt":"2026-06-01T10:00:00Z","updatedAt":"2026-06-01T10:00:00Z",
    "personal":${publications == "[]"},"publications":$publications}"""

private const val CATEGORIES =
    """{"version":"v1","items":[
    {"id":"c1","key":"books","parentId":null,"name":"Книги","sortOrder":1,"system":false},
    {"id":"c2","key":"books.sf","parentId":"c1","name":"Фантастика","sortOrder":2,"system":false},
    {"id":"c-old","key":"old","parentId":null,"name":"Скрытая","sortOrder":3,"system":false,"archived":true},
    {"id":"c-none","key":"none","parentId":null,"name":"Без категории","sortOrder":4,"system":true}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelsTest {
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

    @Test
    fun `теги разбираются по пробелам и запятым без решётки и повторов`() {
        assertEquals(listOf("scifi", "классика", "a"), parseTags("#SciFi, классика  scifi\n#a"))
        assertEquals(emptyList(), parseTags("  , # "))
    }

    @Test
    fun `лента подгружает следующие страницы по курсору и останавливается на последней`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { request ->
                    when (request.url.parameters["cursor"]) {
                        null -> json("""{"items":[${card("a")},${card("b")}],"nextCursor":"c1"}""")
                        "c1" -> json("""{"items":[${card("c")}]}""")
                        else -> error("лишний запрос")
                    }
                }
            val vm = FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            val first = vm.state.first { !it.loading }
            assertEquals(listOf("a", "b"), first.items.map { it.id })
            assertEquals("c1", first.nextCursor)

            vm.loadMore()
            val all = vm.state.first { it.items.size == 3 }
            assertEquals(listOf("a", "b", "c"), all.items.map { it.id })
            assertEquals(null, vm.state.first { !it.loadingMore }.nextCursor)

            vm.loadMore()
            kotlinx.coroutines.yield()
            assertEquals(
                listOf("GET /api/v1/groups/g1/reviews", "GET /api/v1/groups/g1/reviews"),
                fixture.calls(),
                "после последней страницы запросов больше нет",
            )
        }

    @Test
    fun `мои рекомендации читаются без группы`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json("""{"items":[${card("a")}]}""") }
            val vm = FeedViewModel(FeedScope.Mine, fixture.reviewsRepository, fixture.api, fixture.categories)
            vm.state.first { !it.loading && it.items.isNotEmpty() }
            assertTrue("GET /api/v1/me/reviews" in fixture.calls())
        }

    @Test
    fun `карточка показывает правку и удаление только автору`() =
        runTest(dispatcher) {
            val other = """{"id":"u-2","username":"x","displayName":"Икс"}"""
            val mine =
                GroupsFixture { r ->
                    if (r.url.encodedPath.endsWith("/me")) {
                        json(ME_JSON)
                    } else if (r.url.encodedPath.endsWith("/groups")) {
                        json("[]")
                    } else {
                        json(details())
                    }
                }
            val vmMine = ReviewViewModel("r1", mine.reviewsRepository, mine.me, mine.api)
            assertTrue(vmMine.state.first { !it.loading && it.myUserId != null }.isAuthor)

            val foreign =
                GroupsFixture { r ->
                    if (r.url.encodedPath.endsWith("/me")) {
                        json(ME_JSON)
                    } else if (r.url.encodedPath.endsWith("/groups")) {
                        json("[]")
                    } else {
                        json(details(author = other))
                    }
                }
            val vmForeign = ReviewViewModel("r1", foreign.reviewsRepository, foreign.me, foreign.api)
            assertEquals(false, vmForeign.state.first { !it.loading && it.myUserId != null }.isAuthor)
        }

    @Test
    fun `без сети лента показывает сохранённую первую страницу а ошибка остаётся для остального`() =
        runTest(dispatcher) {
            var offline = false
            val fixture =
                GroupsFixture {
                    if (offline) error("нет сети") else json("""{"items":[${card("a")}],"nextCursor":"c1"}""")
                }
            val vm = FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            val online = vm.state.first { !it.loading }
            assertEquals(false, online.offline)
            assertTrue("feed:g1" in fixture.cacheDao.entries)

            offline = true
            vm.refresh()
            val saved = vm.state.first { it.offline && !it.loading }
            assertEquals(listOf("a"), saved.items.map { it.id })
            assertEquals(null, saved.error, "кэш вместо ошибки")

            // следующая страница из кэша не берётся: показывается ошибка сети
            vm.loadMore()
            assertEquals(AppError.Network, vm.state.first { it.error != null }.error)
        }

    @Test
    fun `без сети и без кэша лента показывает ошибку а чужие ошибки сервера кэшем не маскируются`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { error("нет сети") }
            val vm = FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            assertEquals(AppError.Network, vm.state.first { !it.loading }.error)

            val denied = GroupsFixture { json(problem(404, "NOT_FOUND"), HttpStatusCode.NotFound) }
            denied.cacheDao.entries["feed:g1"] =
                CachedResponse("feed:g1", """{"items":[${card("old")}]}""", 0)
            val vm2 = FeedViewModel(FeedScope.Group("g1"), denied.reviewsRepository, denied.api, denied.categories)
            val state = vm2.state.first { !it.loading }
            assertTrue(state.items.isEmpty(), "доступ закрыт: сохранённое не показывается")
            assertEquals(false, state.offline)
        }

    @Test
    fun `карточка без сети берётся из кэша и не даёт править а удаление чистит кэш`() =
        runTest(dispatcher) {
            var offline = false
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.method == HttpMethod.Delete -> json("", HttpStatusCode.NoContent)
                        offline && r.url.encodedPath.contains("/reviews/") -> error("нет сети")
                        r.url.encodedPath.endsWith("/me") -> json(ME_JSON)
                        r.url.encodedPath.endsWith("/groups") -> json("[]")
                        else -> json(details())
                    }
                }
            val vm = ReviewViewModel("r1", fixture.reviewsRepository, fixture.me, fixture.api)
            assertTrue(vm.state.first { !it.loading && it.myUserId != null }.isAuthor)

            offline = true
            vm.load()
            val cached = vm.state.first { it.offline && !it.loading }
            assertEquals("Дюна", cached.review?.title)
            assertEquals(false, cached.isAuthor, "правка и удаление только онлайн")

            offline = false
            vm.delete()
            awaitUntil { "review:r1" !in fixture.cacheDao.entries }
        }

    @Test
    fun `очистка кэша убирает все сохранённые ответы`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json("""{"items":[${card("a")}]}""") }
            FeedViewModel(
                FeedScope.Group("g1"),
                fixture.reviewsRepository,
                fixture.api,
                fixture.categories,
            ).state.first {
                !it.loading
            }
            assertTrue(fixture.cacheDao.entries.isNotEmpty())
            ResponseCache(fixture.cacheDao).clear()
            assertTrue(fixture.cacheDao.entries.isEmpty())
        }

    @Test
    fun `фильтры уходят параметрами запроса а результат с фильтром не кэшируется`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json("""{"items":[${card("a")}]}""") }
            val vm = FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            vm.state.first { !it.loading }
            assertTrue("feed:g1" in fixture.cacheDao.entries, "пустой фильтр кэшируется")
            fixture.cacheDao.entries.clear()

            vm.setFilter(
                ReviewFilter(
                    query = "дюна",
                    categoryIds = setOf("c1", "c2"),
                    tags = setOf("a", "b"),
                    anyTag = true,
                    authorId = "u1",
                ),
            )
            awaitUntil { fixture.requests.size >= 2 }
            vm.state.first { !it.loading && it.items.isNotEmpty() }
            val url = fixture.requests.last().url
            assertEquals("дюна", url.parameters["q"])
            assertEquals("c1,c2", url.parameters["categoryIds"])
            assertEquals("a,b", url.parameters["tags"])
            assertEquals("ANY", url.parameters["tagMode"])
            assertEquals("u1", url.parameters["authorId"])
            assertTrue(
                fixture.cacheDao.entries.keys
                    .none { it.startsWith("feed:") },
                "результат с фильтром в кэш не попадает",
            )
        }

    @Test
    fun `последний фильтр группы запоминается и сбрасывается вместе с фильтром`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json("""{"items":[${card("a")}]}""") }
            val vm = FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            vm.state.first { !it.loading }
            vm.search("  дюна ")
            vm.state.first { it.filter.query == "дюна" && !it.loading }
            awaitUntil { "filter:g1" in fixture.cacheDao.entries }

            // новая ViewModel (повторное открытие экрана) начинает с сохранённого фильтра
            val reopened =
                FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            assertEquals(
                "дюна",
                reopened.state
                    .first { !it.loading }
                    .filter.query,
            )
            assertEquals(
                "дюна",
                fixture.requests
                    .last()
                    .url.parameters["q"],
            )

            reopened.clearFilter()
            awaitUntil { "filter:g1" !in fixture.cacheDao.entries }
        }

    @Test
    fun `поиск по всем группам берёт названия групп и идёт на общий адрес`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    if (r.url.encodedPath.endsWith(
                            "/groups",
                        )
                    ) {
                        json("[${groupJson("g1", "Клуб")}]")
                    } else {
                        json("""{"items":[${card("a")}]}""")
                    }
                }
            val vm = FeedViewModel(FeedScope.AllGroups, fixture.reviewsRepository, fixture.api, fixture.categories)
            val state = vm.state.first { !it.loading && it.items.isNotEmpty() }
            assertEquals(mapOf("g1" to "Клуб"), state.groupNames)
            assertTrue("GET /api/v1/reviews" in fixture.calls())
            assertEquals(0, state.filter.panelCount)
        }

    @Test
    fun `панель фильтров загружает категории теги и участников один раз`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.url.encodedPath.endsWith("/categories") -> json(CATEGORIES)
                        r.url.encodedPath.endsWith("/tags") -> json("""[{"name":"scifi","count":2}]""")
                        r.url.encodedPath.endsWith("/members") -> json("[${memberJson("u1", "ann")}]")
                        else -> json("""{"items":[]}""")
                    }
                }
            val vm = FeedViewModel(FeedScope.Group("g1"), fixture.reviewsRepository, fixture.api, fixture.categories)
            vm.state.first { !it.loading }
            vm.loadOptions()
            val options = vm.state.first { it.options?.tags?.isNotEmpty() == true }.options!!
            assertEquals(listOf("Книги", "Фантастика", "Скрытая", "Без категории"), options.categories.map { it.name })
            assertEquals(listOf("scifi"), options.tags.map { it.name })
            assertEquals(listOf("u1"), options.authors.map { it.userId })
            vm.loadOptions()
            kotlinx.coroutines.yield()
            assertEquals(1, fixture.calls().count { it.endsWith("/tags") }, "повторно не грузится")
        }

    @Test
    fun `форма не отправляет пустое название и рекомендацию без категории`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.url.encodedPath.endsWith("/categories") -> json(CATEGORIES)
                        r.url.encodedPath.endsWith("/groups") -> json("[${groupJson("g1", role = ADMIN_ROLE)}]")
                        else -> error("неожиданный запрос ${r.url}")
                    }
                }
            val vm = ReviewFormViewModel(null, "g1", fixture.reviews, fixture.api, fixture.categories)
            vm.state.first { !it.loading }
            vm.save()
            assertEquals(FieldErrorReason.REQUIRED, vm.state.value.errors["title"])
            assertTrue(vm.state.value.categoryMissing)

            vm.setTitle("Дюна")
            vm.setCategory("c1")
            vm.setTagsText((1..11).joinToString(" ") { "t$it" })
            vm.save()
            assertEquals(FieldErrorReason.TOO_LONG, vm.state.value.errors["tags"])
            assertTrue(fixture.calls().none { it.startsWith("POST") }, "на сервер ничего не ушло")
        }

    @Test
    fun `форма создаёт рекомендацию с публикацией и сообщает идентификатор`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.method == HttpMethod.Post -> json(details("new"), HttpStatusCode.Created)
                        r.url.encodedPath.endsWith("/categories") -> json(CATEGORIES)
                        else -> json("[${groupJson("g1", role = ADMIN_ROLE)}]")
                    }
                }
            val vm = ReviewFormViewModel(null, "g1", fixture.reviews, fixture.api, fixture.categories)
            vm.state.first { !it.loading }
            vm.setTitle("Дюна")
            vm.setCategory("c1")
            vm.setTagsText("#SciFi")
            var saved: String? = null
            val job = launchCollect(vm) { saved = it }
            vm.save()
            awaitUntil { saved != null }
            job.cancel()
            assertEquals("new", saved)
            assertTrue("POST /api/v1/reviews" in fixture.calls())
        }

    @Test
    fun `правка загружает рекомендацию и сохраняет полем и набором публикаций`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.method == HttpMethod.Patch || r.method == HttpMethod.Put -> json(details())
                        r.url.encodedPath.endsWith("/categories") -> json(CATEGORIES)
                        r.url.encodedPath.contains("/reviews/") -> json(details())
                        else -> json("[${groupJson("g1", role = ADMIN_ROLE)}]")
                    }
                }
            val vm = ReviewFormViewModel("r1", null, fixture.reviews, fixture.api, fixture.categories)
            val loaded = vm.state.first { !it.loading && it.title.isNotEmpty() }
            assertEquals("Дюна", loaded.title)
            assertEquals(setOf("g1"), loaded.selected)
            assertEquals("c1", loaded.categoryId)
            assertEquals("scifi", loaded.tagsText)

            var saved: String? = null
            val job = launchCollect(vm) { saved = it }
            vm.save()
            awaitUntil { saved != null }
            job.cancel()
            assertTrue("PATCH /api/v1/reviews/r1" in fixture.calls())
            assertTrue("PUT /api/v1/reviews/r1/groups" in fixture.calls())
        }

    @Test
    fun `Без категории предлагается только личной а скрытые категории не предлагаются`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.method == HttpMethod.Post -> json(details("new", publications = "[]"), HttpStatusCode.Created)
                        r.url.encodedPath.endsWith("/categories") -> json(CATEGORIES)
                        else -> json("[${groupJson("g1", role = ADMIN_ROLE)}]")
                    }
                }
            val vm = ReviewFormViewModel(null, "g1", fixture.reviews, fixture.api, fixture.categories)
            val loaded = vm.state.first { !it.loading }
            assertEquals(listOf("c1", "c2"), loaded.choices.map { it.category.id }, "в группе — без «Без категории»")
            assertEquals(listOf(0, 1), loaded.choices.map { it.depth })

            vm.setTitle("Заметка")
            vm.setCategory("c-none")
            vm.save()
            assertTrue(vm.state.value.categoryMissing, "«Без категории» с группой не отправляется")
            assertTrue(fixture.calls().none { it.startsWith("POST") })

            vm.toggleGroup("g1")
            assertEquals(
                listOf("c1", "c2", "c-none"),
                vm.state.value.choices
                    .map { it.category.id },
            )
            var saved: String? = null
            val job = launchCollect(vm) { saved = it }
            vm.save()
            awaitUntil { saved != null }
            job.cancel()
            val body =
                (fixture.requests.last { it.method == HttpMethod.Post }.body as io.ktor.http.content.TextContent)
                    .text
            assertTrue("\"categoryId\":\"c-none\"" in body && "groupIds" !in body, "личная: групп нет — $body")
        }

    @Test
    fun `набор категорий сверяется по версии и без сети берётся сохранённый`() =
        runTest(dispatcher) {
            var mode = "fresh"
            val fixture =
                GroupsFixture { r ->
                    when (mode) {
                        "fresh" -> json(CATEGORIES)
                        "same" -> json("", HttpStatusCode.NotModified)
                        else -> error("нет сети")
                    }.also { assertTrue(r.url.encodedPath.endsWith("/categories")) }
                }
            assertEquals(
                "v1",
                fixture.categories
                    .set()
                    .getOrThrow()
                    .version,
            )
            assertEquals(null, fixture.requests.single().headers["If-None-Match"])

            mode = "same"
            assertEquals(
                4,
                fixture.categories
                    .set()
                    .getOrThrow()
                    .items.size,
                "304 — сохранённый набор",
            )
            assertEquals("\"v1\"", fixture.requests.last().headers["If-None-Match"])

            mode = "offline"
            assertEquals(
                "v1",
                fixture.categories
                    .set()
                    .getOrThrow()
                    .version,
            )
        }

    @Test
    fun `правка в личную закрывает замок до смены категории`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    when {
                        r.method == HttpMethod.Patch || r.method == HttpMethod.Put -> json(details())
                        r.url.encodedPath.endsWith("/categories") -> json(CATEGORIES)
                        r.url.encodedPath.contains("/reviews/") -> json(details())
                        else -> json("[${groupJson("g1", role = ADMIN_ROLE)}]")
                    }
                }
            val vm = ReviewFormViewModel("r1", null, fixture.reviews, fixture.api, fixture.categories)
            vm.state.first { !it.loading && it.title.isNotEmpty() }
            vm.toggleGroup("g1")
            vm.setCategory("c-none")
            var saved: String? = null
            val job = launchCollect(vm) { saved = it }
            vm.save()
            awaitUntil { saved != null }
            job.cancel()
            val writes = fixture.calls().filter { it.startsWith("PUT") || it.startsWith("PATCH") }
            assertEquals(listOf("PUT /api/v1/reviews/r1/groups", "PATCH /api/v1/reviews/r1"), writes)
        }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(
        vm: ReviewFormViewModel,
        onSaved: (String) -> Unit,
    ) = launch(Dispatchers.Default) {
        vm.effects.collect { if (it is ReviewFormEffect.Saved) onSaved(it.reviewId) }
    }
}
