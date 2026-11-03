package app.podgon.mobile.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.MeApi
import app.podgon.mobile.designsystem.MainTab
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainNavState(
    val tab: MainTab = MainTab.FEED,
    /** Группа открытой вкладки ленты: «+» передаёт её в форму. */
    val feedGroupId: String? = null,
    /** Системные группы из профиля: лента «Личного» и «Избранного». */
    val personalGroupId: String? = null,
    val favoritesGroupId: String? = null,
    /** Открыт экран «Группы»: «+» открывает окно «Создать / Присоединиться» вместо новой рекомендации. */
    val groupsPlus: Boolean = false,
    /** Шаг сценария (вступление в группу): нижнее меню скрыто. */
    val barHidden: Boolean = false,
    val error: AppError? = null,
)

/**
 * Нижнее меню: выбранный раздел, у каждого — свой стек экранов. Повторное нажатие на активный пункт
 * возвращает к началу его стека ([reselected]). «+» в ленте создаёт рекомендацию в группе текущей вкладки,
 * в остальных разделах — личную.
 */
class MainNavigationViewModel(
    private val me: MeApi,
) : ViewModel() {
    private val _state = MutableStateFlow(MainNavState())
    val state: StateFlow<MainNavState> = _state.asStateFlow()

    private val _reselected = MutableSharedFlow<MainTab>(extraBufferCapacity = 1)
    val reselected: SharedFlow<MainTab> = _reselected.asSharedFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val result = me.me()
            _state.update {
                val profile = result.getOrNull()
                it.copy(
                    personalGroupId = profile?.personalGroupId ?: it.personalGroupId,
                    favoritesGroupId = profile?.favoritesGroupId ?: it.favoritesGroupId,
                    error = result.appError(),
                )
            }
        }
    }

    fun select(tab: MainTab) {
        if (_state.value.tab == tab) {
            _reselected.tryEmit(tab)
        } else {
            _state.update { it.copy(tab = tab) }
        }
    }

    fun setFeedGroup(groupId: String?) = _state.update { it.copy(feedGroupId = groupId) }

    private val _groupsPlusPressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Нажатие «+» на экране «Группы». */
    val groupsPlusPressed: SharedFlow<Unit> = _groupsPlusPressed.asSharedFlow()

    fun setGroupsScreen(shown: Boolean) = _state.update { it.copy(groupsPlus = shown) }

    fun setBarHidden(hidden: Boolean) = _state.update { it.copy(barHidden = hidden) }

    /** «+» нижнего меню: на «Группах» — окно групп, иначе — новая рекомендация ([onReview] с группой). */
    fun plus(onReview: (String?) -> Unit) {
        if (_state.value.groupsPlus) _groupsPlusPressed.tryEmit(Unit) else onReview(newReviewGroup())
    }

    /** Группа, отмеченная в новой рекомендации: только из ленты; в «Личном», «Избранном», «Настройках» — личная. */
    fun newReviewGroup(): String? = _state.value.let { if (it.tab == MainTab.FEED) it.feedGroupId else null }
}
