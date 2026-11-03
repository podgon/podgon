package app.podgon.mobile

import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.network.SavedFiltersApi
import app.podgon.mobile.core.settings.LanguageSettings
import app.podgon.mobile.core.settings.ThemeSettings
import app.podgon.mobile.designsystem.MainTab
import app.podgon.mobile.designsystem.ThemeMode
import app.podgon.mobile.feature.main.MainNavigationViewModel
import app.podgon.mobile.feature.settings.SettingsViewModel
import app.podgon.mobile.testing.GroupsFixture
import app.podgon.mobile.testing.ME_JSON
import app.podgon.mobile.testing.groupJson
import app.podgon.mobile.testing.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class Stage823NavigationTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `нижнее меню переключает разделы а повторное нажатие возвращает к началу`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json(ME_JSON) }
            val vm = MainNavigationViewModel(fixture.me)
            val loaded = vm.state.first { it.personalGroupId != null }
            assertEquals("g-personal", loaded.personalGroupId)
            assertEquals("g-favorites", loaded.favoritesGroupId)

            val reselected = mutableListOf<MainTab>()
            val job = launch { vm.reselected.collect { reselected += it } }
            advanceUntilIdle()
            vm.select(MainTab.SETTINGS)
            assertEquals(MainTab.SETTINGS, vm.state.value.tab)
            vm.select(MainTab.SETTINGS)
            advanceUntilIdle()
            assertEquals(listOf(MainTab.SETTINGS), reselected)
            job.cancel()
        }

    @Test
    fun `плюс в ленте берёт группу вкладки а в остальных разделах создаёт личную`() =
        runTest(dispatcher) {
            val vm = MainNavigationViewModel(GroupsFixture { json(ME_JSON) }.me)
            vm.setFeedGroup("g1")
            assertEquals("g1", vm.newReviewGroup())
            vm.select(MainTab.PERSONAL)
            assertNull(vm.newReviewGroup())
            vm.select(MainTab.FEED)
            vm.setFeedGroup(null)
            assertNull(vm.newReviewGroup())
        }

    @Test
    fun `выбор темы сохраняется на устройстве и переживает выход из аккаунта`() =
        runTest(dispatcher) {
            val fixture = GroupsFixture { json("[]") }
            val cache = ResponseCache(fixture.cacheDao)
            ThemeSettings(cache).set(ThemeMode.DARK)
            cache.write("feed:g1", "x")
            cache.clear()
            assertEquals(setOf("pref:theme"), fixture.cacheDao.entries.keys)

            val restored = ThemeSettings(cache)
            assertEquals(ThemeMode.SYSTEM, restored.mode.value)
            restored.load()
            assertEquals(ThemeMode.DARK, restored.mode.value)
        }

    @Test
    fun `настройки показывают число групп и закреплённых секций`() =
        runTest(dispatcher) {
            val fixture =
                GroupsFixture { r ->
                    if (r.url.encodedPath.endsWith("/me/filters")) {
                        json(
                            """[{"id":"f1","kind":"FILTER","inFeed":true,"name":"А","position":0},
                            {"id":"f2","kind":"FILTER","inFeed":false,"name":"Б"}]""",
                        )
                    } else {
                        json("[${groupJson("g1")},${groupJson("g2")}]")
                    }
                }
            val theme = ThemeSettings(ResponseCache(fixture.cacheDao))
            val vm =
                SettingsViewModel(
                    fixture.api,
                    SavedFiltersApi(fixture.client),
                    theme,
                    LanguageSettings(MemoryLanguageStore()),
                    fixture.me,
                )
            vm.load()
            val state = vm.state.first { it.groups != null }
            assertEquals(2, state.groups)
            assertEquals(1, state.pinned)
            vm.setTheme(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, vm.themeMode.first { it == ThemeMode.LIGHT })
        }
}
