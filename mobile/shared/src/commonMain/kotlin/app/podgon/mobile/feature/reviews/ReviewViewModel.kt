package app.podgon.mobile.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.ReviewDetails
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.core.network.ReviewsApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewState(
    val loading: Boolean = true,
    val review: ReviewDetails? = null,
    val myUserId: String? = null,
    /** Названия моих групп по идентификатору: подписи публикаций. */
    val groupNames: Map<String, String> = emptyMap(),
    val error: AppError? = null,
) {
    val isAuthor: Boolean get() = review != null && review.author.id == myUserId
}

/** Рекомендация удалена — экран закрывается. */
sealed interface ReviewEffect {
    data object Closed : ReviewEffect
}

/** Карточка рекомендации: чтение, для автора — удаление (правка — на отдельном экране). */
class ReviewViewModel(
    private val reviewId: String,
    private val api: ReviewsApi,
    private val me: MeApi,
    private val groups: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val review = api.get(reviewId)
            val profile = me.me()
            val myGroups = groups.list()
            _state.update {
                it.copy(
                    loading = false,
                    review = review.getOrNull() ?: it.review,
                    myUserId = profile.getOrNull()?.id ?: it.myUserId,
                    groupNames = myGroups.getOrNull()?.associate { g -> g.id to g.name } ?: it.groupNames,
                    error = review.appError(),
                )
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            val result = api.delete(reviewId)
            if (result.isSuccess) {
                _effects.send(
                    ReviewEffect.Closed,
                )
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }
}
