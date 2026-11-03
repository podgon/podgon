package app.podgon.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** Превью общих компонентов в светлой и тёмной теме (данные выдуманные). */
@Composable
private fun Showcase(mode: ThemeMode) {
    PodgonTheme(mode) {
        Column(Modifier.background(podgonColors.background)) {
            PodgonTopBar(offline = true, onNotifications = {}, onProfile = {})
            SearchRow(query = "", onQueryChange = {}, onSearch = {}, activeFilters = 2, onFilters = {})
            FilterChipsRow(listOf("Кофейни", "#кофе"), onRemove = {})
            PillTabs(
                listOf(PillTab("all", "Все"), PillTab("f", "Друзья"), PillTab("w", "Работа")),
                selectedId = "all",
                onSelect = {},
            )
            SectionHeader("Кофейни", count = 12, expanded = true, onToggle = {}, mark = SectionMark.FILTER)
            ReviewRow(
                "Флэт уайт на Покровке",
                9,
                {},
                category = "Кофейни",
                author = "Анна",
                date = "14:05",
                favorite = false,
            )
            ReviewRow("Дюна", 8, {}, category = "Фантастика", author = "Иван", date = "27 окт.", favorite = true)
            SectionHeader("Книги", count = 3, expanded = false, onToggle = {}, mark = SectionMark.PINNED)
            PodgonBottomBar(MainTab.FEED, onSelect = {}, onPlus = {})
        }
    }
}

@Preview
@Composable
private fun ShowcaseLight() = Showcase(ThemeMode.LIGHT)

@Preview
@Composable
private fun ShowcaseDark() = Showcase(ThemeMode.DARK)

@Preview
@Composable
private fun StatesPreview() {
    PodgonTheme(ThemeMode.LIGHT) {
        Column(Modifier.background(podgonColors.background)) {
            FeedSkeleton(sections = 1)
            EmptyState(
                "Здесь пока пусто",
                "Создайте первую рекомендацию или вступите в группу.",
                actions =
                    listOf(
                        EmptyAction("Новая рекомендация", true) {},
                        EmptyAction("Вступить в группу", false) {},
                    ),
            )
        }
    }
}
