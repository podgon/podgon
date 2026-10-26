package app.podgon.mobile

import app.podgon.contract.CategoryInfo
import app.podgon.contract.ErrorCode
import app.podgon.contract.FieldErrorReason
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.feature.groups.CategoriesViewModel
import app.podgon.mobile.feature.groups.flattenTree
import app.podgon.mobile.feature.groups.subtreeIds
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.json
import app.podgon.mobile.testing.problem
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
class CategoriesViewModelTest {
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

    private fun cat(
        id: String,
        parent: String? = null,
        name: String = id,
    ) = CategoryInfo(id, parent, name, "")

    private val listJson =
        """[{"id":"a","parentId":null,"name":"Книги","description":""},
            {"id":"b","parentId":"a","name":"Фантастика","description":""},
            {"id":"c","parentId":null,"name":"Фильмы","description":""}]"""

    @Test
    fun `дерево обходится в глубину и уровни считаются от корня`() {
        val flat = listOf(cat("a"), cat("c"), cat("b", "a"), cat("d", "b"), cat("orphan", "missing"))
        val tree = flattenTree(flat)
        assertEquals(listOf("a", "b", "d", "c"), tree.map { it.category.id })
        assertEquals(listOf(0, 1, 2, 0), tree.map { it.depth })
    }

    @Test
    fun `поддерево включает саму категорию и всех потомков`() {
        val flat = listOf(cat("a"), cat("b", "a"), cat("d", "b"), cat("c"))
        assertEquals(setOf("a", "b", "d"), subtreeIds(flat, "a"))
        assertEquals(setOf("c"), subtreeIds(flat, "c"))
    }

    @Test
    fun `категории загружаются в виде дерева`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json(listJson) }
            val vm = CategoriesViewModel("g1", fixture.api)
            val loaded = vm.state.first { !it.loading }
            assertEquals(listOf("Книги", "Фантастика", "Фильмы"), loaded.tree.map { it.category.name })
            assertTrue("GET /api/v1/groups/g1/categories" in fixture.calls())
        }

    @Test
    fun `пустое название не уходит на сервер а конфликт имени показывается`() =
        runTest(dispatcher) {
            var conflict = false
            val fixture =
                GroupsFixture { request ->
                    when {
                        request.method == HttpMethod.Post && conflict ->
                            json(
                                problem(409, "CATEGORY_NAME_TAKEN"),
                                HttpStatusCode.Conflict,
                            )
                        request.method == HttpMethod.Post ->
                            json(
                                """{"id":"n","parentId":"a","name":"Новая","description":""}""",
                                HttpStatusCode.Created,
                            )
                        else -> json(listJson)
                    }
                }
            val vm = CategoriesViewModel("g1", fixture.api)
            vm.state.first { !it.loading }
            var done = false
            vm.create("   ", "", null) { done = true }
            assertEquals(FieldErrorReason.REQUIRED, vm.state.value.nameError)
            assertEquals(1, fixture.requests.size, "только начальная загрузка")

            conflict = true
            vm.create("Книги", "", null) { done = true }
            vm.state.first { it.error != null }
            assertEquals(ErrorCode.CATEGORY_NAME_TAKEN, (vm.state.value.error as AppError.Api).code)
            assertEquals(false, done)

            conflict = false
            vm.create("Новая", "", "a") { done = true }
            awaitUntil { done }
            assertTrue("POST /api/v1/groups/g1/categories" in fixture.calls())
        }

    @Test
    fun `удаление сначала узнаёт последствия и выполняется только после подтверждения`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { request ->
                    when {
                        request.method == HttpMethod.Delete -> json("", HttpStatusCode.NoContent)
                        request.url.encodedPath.endsWith(
                            "/delete-impact",
                        ) -> json("""{"subcategories":1,"publications":0}""")
                        else -> json(listJson)
                    }
                }
            val vm = CategoriesViewModel("g1", fixture.api)
            val loaded = vm.state.first { !it.loading }
            vm.requestDelete(loaded.categories.first())
            val pending = vm.state.first { it.pendingDelete != null }.pendingDelete!!
            assertEquals(1, pending.impact.subcategories)
            assertTrue(fixture.calls().none { it.startsWith("DELETE") }, "до подтверждения ничего не удаляется")

            vm.cancelDelete()
            assertNull(vm.state.value.pendingDelete)
            assertTrue(fixture.calls().none { it.startsWith("DELETE") })

            vm.requestDelete(loaded.categories.first())
            vm.state.first { it.pendingDelete != null }
            vm.confirmDelete()
            awaitUntil { "DELETE /api/v1/groups/g1/categories/a" in fixture.calls() }
        }

    @Test
    fun `перенос отправляет нового родителя`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { request ->
                    if (request.method == HttpMethod.Put) {
                        json("""{"id":"b","parentId":null,"name":"Фантастика","description":""}""")
                    } else {
                        json(listJson)
                    }
                }
            val vm = CategoriesViewModel("g1", fixture.api)
            val loaded = vm.state.first { !it.loading }
            var done = false
            vm.move(loaded.categories[1], null) { done = true }
            awaitUntil { done }
            assertTrue("PUT /api/v1/groups/g1/categories/b/parent" in fixture.calls())
        }
}
