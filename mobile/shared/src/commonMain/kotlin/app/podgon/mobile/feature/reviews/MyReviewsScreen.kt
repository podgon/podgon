package app.podgon.mobile.feature.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podgon.mobile.designsystem.BottomBarSpace
import app.podgon.mobile.designsystem.EmptyState
import app.podgon.mobile.designsystem.FeedSkeleton
import app.podgon.mobile.designsystem.PodgonIconButton
import app.podgon.mobile.designsystem.PodgonIcons
import app.podgon.mobile.designsystem.ReviewRow
import app.podgon.mobile.designsystem.podgonColors
import app.podgon.mobile.feature.groups.RetryError
import app.podgon.mobile.feature.home.shortDate
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.a11y_back
import app.podgon.mobile.resources.months_short
import app.podgon.mobile.resources.my_reviews
import app.podgon.mobile.resources.my_reviews_empty_text
import app.podgon.mobile.resources.my_reviews_empty_title
import org.jetbrains.compose.resources.stringResource

/** «Мои рекомендации» (И21): групповые, где я автор, новые сверху; строка — оценка, категория, группы. */
@Composable
fun MyReviewsScreen(
    viewModel: FeedViewModel,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = podgonColors
    val months = stringResource(Res.string.months_short).split(',')
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PodgonIconButton(PodgonIcons.Back, stringResource(Res.string.a11y_back), onBack)
            Text(stringResource(Res.string.my_reviews), style = MaterialTheme.typography.headlineSmall, color = c.ink)
        }
        state.error?.let { RetryError(it, viewModel::refresh) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(bottom = BottomBarSpace),
        ) {
            if (state.loading && state.items.isEmpty()) item { FeedSkeleton(sections = 1, rows = 5) }
            if (!state.loading && state.items.isEmpty() && state.error == null) {
                item {
                    EmptyState(
                        stringResource(Res.string.my_reviews_empty_title),
                        stringResource(Res.string.my_reviews_empty_text),
                    )
                }
            }
            items(state.items, key = { it.id }) { card ->
                ReviewRow(
                    title = card.title,
                    rating = card.rating,
                    onClick = { onOpen(card.id) },
                    category = state.categories[card.categoryId]?.name,
                    author =
                        card.groupIds
                            .mapNotNull { state.groupNames[it] }
                            .joinToString(", ")
                            .ifEmpty { null },
                    date = shortDate(card.sharedAt, months),
                )
            }
            if (state.nextCursor != null) item { LaunchedEffect(state.items.size) { viewModel.loadMore() } }
        }
    }
}
