package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CATEGORY_NONE_KEY
import app.podgon.contract.InvitationPreview
import app.podgon.contract.ShareMode
import app.podgon.contract.SharePreview
import app.podgon.contract.ShareRule
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.feature.reviews.CategoryNode
import app.podgon.mobile.feature.reviews.CategoryRepository
import app.podgon.mobile.feature.reviews.flattenTree
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinGroupState(
    val token: String = "",
    /** Сведения о группе после проверки кода: пользователь подтверждает вступление. */
    val preview: InvitationPreview? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
    val mode: ShareMode = ShareMode.NONE,
    val selectedCategories: Set<String> = emptySet(),
    val tagsText: String = "",
    /** Категории набора для правил «из категорий» и «кроме категорий» (без «Без категории»: она только у личных). */
    val categories: List<CategoryNode> = emptyList(),
    /** Что будет перенесено по текущему правилу (`null` — правило не задано или ещё считается). */
    val sharePreview: SharePreview? = null,
    val sharePreviewLoading: Boolean = false,
) {
    val tags: List<String>
        get() =
            tagsText
                .split(',', ';', ' ', '\n')
                .map(Validation::normalizeTag)
                .filter { it.isNotEmpty() && Validation.tag(it) == null }
                .distinct()
                .take(Validation.TAGS_MAX)

    /** Правило переноса; `null` — ничего не переносим или правило ещё не полное (не выбраны категории/теги). */
    val rule: ShareRule?
        get() =
            when (mode) {
                ShareMode.NONE -> null
                ShareMode.ALL -> ShareRule(ShareMode.ALL)
                ShareMode.CATEGORIES, ShareMode.CATEGORIES_EXCEPT ->
                    selectedCategories
                        .takeIf { it.isNotEmpty() }
                        ?.let { ShareRule(mode, categoryIds = it.toList()) }
                ShareMode.TAGS, ShareMode.TAGS_EXCEPT ->
                    tags.takeIf { it.isNotEmpty() }?.let {
                        ShareRule(
                            mode,
                            tags = it,
                        )
                    }
            }
}

/** Вступление по коду: ввод кода → предпросмотр группы → правило переноса своих рекомендаций → подтверждение. */
class JoinGroupViewModel(
    private val api: GroupsApi,
    private val categorySet: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinGroupState())
    val state: StateFlow<JoinGroupState> = _state.asStateFlow()

    private val _joined = Channel<String>(Channel.BUFFERED)
    val joined = _joined.receiveAsFlow()

    private var previewJob: Job? = null

    fun onTokenChange(value: String) {
        previewJob?.cancel()
        _state.update { JoinGroupState(token = value.trim()) }
    }

    fun check() {
        val current = _state.value
        if (current.loading || current.token.isEmpty()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.previewInvitation(current.token)
            _state.update { it.copy(loading = false, preview = result.getOrNull(), error = result.appError()) }
        }
    }

    fun setMode(mode: ShareMode) {
        _state.update { it.copy(mode = mode) }
        if (mode == ShareMode.CATEGORIES || mode == ShareMode.CATEGORIES_EXCEPT) loadCategories()
        refreshSharePreview()
    }

    fun toggleCategory(id: String) {
        _state.update {
            it.copy(
                selectedCategories =
                    if (id in
                        it.selectedCategories
                    ) {
                        it.selectedCategories - id
                    } else {
                        it.selectedCategories + id
                    },
            )
        }
        refreshSharePreview()
    }

    fun onTagsChange(value: String) {
        _state.update { it.copy(tagsText = value) }
    }

    /** Пересчитывает предпросмотр переноса (для тегов — по нажатию, для остальных правил — сразу). */
    fun refreshSharePreview() {
        previewJob?.cancel()
        val rule = _state.value.rule
        if (rule == null) {
            _state.update { it.copy(sharePreview = null, sharePreviewLoading = false) }
            return
        }
        _state.update { it.copy(sharePreviewLoading = true, error = null) }
        previewJob =
            viewModelScope.launch {
                val result = api.sharePreview(_state.value.token, rule)
                _state.update {
                    it.copy(
                        sharePreviewLoading = false,
                        sharePreview = result.getOrNull(),
                        error = result.appError(),
                    )
                }
            }
    }

    fun confirm() {
        val current = _state.value
        if (current.loading || current.preview == null) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.acceptInvitation(current.token, current.rule.takeIf { current.sharePreview != null })
            _state.update { it.copy(loading = false, error = result.appError()) }
            result.getOrNull()?.let { _joined.send(it.id) }
        }
    }

    /** Категории общего набора — для выбора в правилах «из категорий» и «кроме категорий». */
    private fun loadCategories() {
        if (_state.value.categories.isNotEmpty()) return
        viewModelScope.launch {
            val result = categorySet.set()
            val nodes = flattenTree(result.getOrNull()?.items.orEmpty()).filter { it.category.key != CATEGORY_NONE_KEY }
            _state.update { it.copy(categories = nodes, error = it.error ?: result.appError()) }
        }
    }
}
