package app.podgon.mobile.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.ReviewCard
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.ReviewsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<ReviewCard> = emptyList(),
    /** `null` — страниц больше нет. */
    val nextCursor: String? = null,
    val error: AppError? = null,
)

/** Лента группы ([groupId]) либо «мои рекомендации» (`groupId == null`) с подгрузкой страниц. */
class FeedViewModel(
    private val groupId: String?,
    private val api: ReviewsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Загружает первую страницу заново (старый список остаётся на экране при ошибке). */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = page(null)
            _state.update {
                val page = result.getOrNull()
                it.copy(
                    loading = false,
                    items = page?.items ?: it.items,
                    nextCursor = if (page != null) page.nextCursor else it.nextCursor,
                    error = result.appError(),
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            val result = page(cursor)
            _state.update {
                val page = result.getOrNull()
                it.copy(
                    loadingMore = false,
                    items = if (page != null) it.items + page.items else it.items,
                    nextCursor = if (page != null) page.nextCursor else it.nextCursor,
                    error = result.appError(),
                )
            }
        }
    }

    private suspend fun page(cursor: String?) = if (groupId == null) api.mine(cursor) else api.feed(groupId, cursor)
}
