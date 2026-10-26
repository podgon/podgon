package app.podgon.mobile.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CategoryInfo
import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.GroupSummary
import app.podgon.contract.PublicationRequest
import app.podgon.contract.SetPublicationsRequest
import app.podgon.contract.UpdateReviewRequest
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import app.podgon.mobile.core.network.ReviewsApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewFormState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val title: String = "",
    val description: String = "",
    val rating: Int = DEFAULT_RATING,
    /** Теги одной строкой: через пробел или запятую, `#` необязателен. */
    val tagsText: String = "",
    val groups: List<GroupSummary> = emptyList(),
    val categories: Map<String, List<CategoryInfo>> = emptyMap(),
    /** Выбранные для публикации группы и категория в каждой (`null` — ещё не выбрана). */
    val selected: Map<String, String?> = emptyMap(),
    val errors: Map<String, FieldErrorReason> = emptyMap(),
    /** Для выбранной группы не выбрана категория. */
    val categoryMissing: Boolean = false,
    val error: AppError? = null,
) {
    val tags: List<String> get() = parseTags(tagsText)
}

/** Разбирает строку тегов: разделители — пробелы и запятые; нормализация и повторы — как на сервере. */
fun parseTags(text: String): List<String> =
    text
        .split(' ', ',', '\n', '\t')
        .map(Validation::normalizeTag)
        .filter { it.isNotEmpty() }
        .distinct()

/** Рекомендация сохранена — экран закрывается, открывается её карточка. */
sealed interface ReviewFormEffect {
    data class Saved(
        val reviewId: String,
    ) : ReviewFormEffect
}

/**
 * Форма создания ([reviewId] `null`) и правки рекомендации: поля, теги, группы с категорией для каждой.
 * [presetGroupId] — группа, из которой открыта форма: сразу отмечена для публикации.
 */
class ReviewFormViewModel(
    private val reviewId: String?,
    private val presetGroupId: String?,
    private val reviews: ReviewsApi,
    private val groups: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewFormState())
    val state: StateFlow<ReviewFormState> = _state.asStateFlow()

    private val _effects = Channel<ReviewFormEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val myGroups = groups.list()
            val existing = reviewId?.let { reviews.get(it) }
            val review = existing?.getOrNull()
            val selected =
                when {
                    review != null -> review.publications.associate { it.groupId to it.categoryId as String? }
                    presetGroupId != null -> mapOf<String, String?>(presetGroupId to null)
                    else -> emptyMap()
                }
            val categories = selected.keys.associateWith { groups.categories(it).getOrNull().orEmpty() }
            _state.update {
                it.copy(
                    loading = false,
                    groups = myGroups.getOrNull() ?: it.groups,
                    categories = it.categories + categories,
                    selected = if (reviewId == null || review != null) selected else it.selected,
                    title = review?.title ?: it.title,
                    description = review?.description ?: it.description,
                    rating = review?.rating ?: it.rating,
                    tagsText = review?.tags?.joinToString(" ") ?: it.tagsText,
                    error = myGroups.appError() ?: existing?.appError(),
                )
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, errors = it.errors - "title") }

    fun setDescription(value: String) =
        _state.update { it.copy(description = value, errors = it.errors - "description") }

    fun setRating(value: Int) =
        _state.update {
            it.copy(rating = value.coerceIn(Validation.RATING_MIN, Validation.RATING_MAX))
        }

    fun setTagsText(value: String) = _state.update { it.copy(tagsText = value, errors = it.errors - "tags") }

    /** Отмечает или снимает группу; при отметке подгружает её категории. */
    fun toggleGroup(groupId: String) {
        val current = _state.value
        if (groupId in current.selected) {
            _state.update { it.copy(selected = it.selected - groupId, categoryMissing = false) }
            return
        }
        _state.update { it.copy(selected = it.selected + (groupId to null)) }
        if (groupId !in current.categories) {
            viewModelScope.launch {
                val result = groups.categories(groupId)
                _state.update {
                    it.copy(
                        categories = it.categories + (groupId to result.getOrNull().orEmpty()),
                        error = result.appError(),
                    )
                }
            }
        }
    }

    fun setCategory(
        groupId: String,
        categoryId: String,
    ) = _state.update { it.copy(selected = it.selected + (groupId to categoryId), categoryMissing = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val title = s.title.trim()
        val description = s.description.trim()
        val tags = s.tags
        val errors =
            listOfNotNull(
                Validation.reviewTitle(title)?.let { "title" to it },
                Validation.reviewDescription(description)?.let { "description" to it },
                Validation.tags(tags)?.let { "tags" to it },
            ).toMap()
        val missing = s.selected.values.any { it == null }
        if (errors.isNotEmpty() || missing) {
            _state.update { it.copy(errors = errors, categoryMissing = missing) }
            return
        }
        val publications = s.selected.map { (groupId, categoryId) -> PublicationRequest(groupId, categoryId!!) }
        _state.update { it.copy(saving = true, errors = emptyMap(), categoryMissing = false, error = null) }
        viewModelScope.launch {
            val result =
                if (reviewId == null) {
                    reviews.create(CreateReviewRequest(title, description, s.rating, tags, publications))
                } else {
                    val updated = reviews.update(reviewId, UpdateReviewRequest(title, description, s.rating, tags))
                    if (updated.isSuccess) {
                        reviews.setPublications(
                            reviewId,
                            SetPublicationsRequest(publications),
                        )
                    } else {
                        updated
                    }
                }
            val saved = result.getOrNull()
            if (saved != null) {
                _state.update { it.copy(saving = false) }
                _effects.send(ReviewFormEffect.Saved(saved.id))
            } else {
                _state.update { it.copy(saving = false, error = result.appError()) }
            }
        }
    }
}

private const val DEFAULT_RATING = 7
