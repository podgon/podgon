package app.podgon.mobile.feature.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CATEGORY_NONE_KEY
import app.podgon.contract.Category
import app.podgon.contract.CreateReviewRequest
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.GroupSummary
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
    /** `null` — оценка ещё не выбрана («—/10»): обязательна. */
    val rating: Int? = null,
    /** Теги одной строкой: через пробел или запятую, `#` необязателен. */
    val tagsText: String = "",
    val groups: List<GroupSummary> = emptyList(),
    /** Общий набор категорий. */
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    /** Группы публикации; пусто — рекомендация личная. */
    val selected: Set<String> = emptySet(),
    /** Замок открыт: рекомендация будет видна в выбранных группах (пустой набор при открытом замке — ошибка). */
    val lockOpen: Boolean = false,
    /** Группы, где рекомендация уже опубликована (при правке): закрытие замка снимает их — нужно подтверждение. */
    val publishedIn: Set<String> = emptySet(),
    /** Подсказки тегов (видимые мне теги по началу ввода). */
    val tagSuggestions: List<String> = emptyList(),
    val errors: Map<String, FieldErrorReason> = emptyMap(),
    /** Категория не выбрана, или «Без категории» выбрана у рекомендации с группами. */
    val categoryMissing: Boolean = false,
    val error: AppError? = null,
) {
    val tags: List<String> get() = parseTags(tagsText)

    val category: Category? get() = categories.firstOrNull { it.id == categoryId }

    /** Полный путь категории: «Рестораны › Кофейни». */
    val categoryPath: String?
        get() =
            category?.let { cat ->
                listOfNotNull(
                    cat.parentId?.let { p ->
                        categories.firstOrNull { it.id == p }?.name
                    },
                    cat.name,
                ).joinToString(" › ")
            }

    /** Чего не хватает для сохранения (для серой кнопки и описания для диктора). */
    val missing: List<String>
        get() =
            listOfNotNull(
                "title".takeIf { title.isBlank() },
                "rating".takeIf { rating == null },
                "category".takeIf {
                    categoryId == null || (selected.isNotEmpty() && category?.key == CATEGORY_NONE_KEY)
                },
                "groups".takeIf { lockOpen && selected.isEmpty() },
            )

    /** Категории для выбора: «Без категории» — только у личной рекомендации. */
    val choices: List<CategoryNode>
        get() = flattenTree(categories).filter { selected.isEmpty() || it.category.key != CATEGORY_NONE_KEY }
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
 * Форма создания ([reviewId] `null`) и правки рекомендации: поля, категория из общего набора, теги, группы
 * публикации (без групп — личная). [presetGroupId] — группа, из которой открыта форма: сразу отмечена.
 */
class ReviewFormViewModel(
    private val reviewId: String?,
    private val presetGroupId: String?,
    private val reviews: ReviewsApi,
    private val groups: GroupsApi,
    private val categories: CategoryRepository,
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
            val set = categories.set()
            val existing = reviewId?.let { reviews.get(it) }
            val review = existing?.getOrNull()
            val selected =
                when {
                    review != null -> review.publications.map { it.groupId }.toSet()
                    presetGroupId != null -> setOf(presetGroupId)
                    else -> emptySet()
                }
            val known = reviewId == null || review != null
            _state.update {
                it.copy(
                    loading = false,
                    groups = myGroups.getOrNull() ?: it.groups,
                    categories = set.getOrNull()?.items ?: it.categories,
                    categoryId = review?.categoryId ?: it.categoryId,
                    selected = if (known) selected else it.selected,
                    lockOpen = if (known) selected.isNotEmpty() else it.lockOpen,
                    publishedIn = review?.publications?.map { p -> p.groupId }?.toSet() ?: it.publishedIn,
                    title = review?.title ?: it.title,
                    description = review?.description ?: it.description,
                    rating = review?.rating ?: it.rating,
                    tagsText = review?.tags?.joinToString(" ") ?: it.tagsText,
                    error = myGroups.appError() ?: set.appError() ?: existing?.appError(),
                )
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, errors = it.errors - "title") }

    fun setDescription(value: String) =
        _state.update { it.copy(description = value, errors = it.errors - "description") }

    fun setRating(value: Int) =
        _state.update {
            it.copy(
                rating = value.coerceIn(Validation.RATING_MIN, Validation.RATING_MAX),
                errors = it.errors - "rating",
            )
        }

    /** Замок: закрытый — личная (группы очищаются), открытый — выбрать группы (набор тоже очищается). */
    fun setLockOpen(open: Boolean) =
        _state.update { it.copy(lockOpen = open, selected = emptySet(), categoryMissing = false) }

    /** Добавляет тег чипом (нормализация — как на сервере). */
    fun addTag(raw: String) {
        val tag = Validation.normalizeTag(raw)
        if (tag.isEmpty()) return
        _state.update { s ->
            s.copy(
                tagsText = (s.tags + tag).distinct().joinToString(" "),
                errors = s.errors - "tags",
                tagSuggestions = emptyList(),
            )
        }
    }

    fun removeTag(tag: String) = _state.update { s -> s.copy(tagsText = (s.tags - tag).joinToString(" ")) }

    /** Подсказки тегов по началу ввода — из тегов моих групп и «Личного». */
    fun suggestTags(prefix: String) {
        val p = Validation.normalizeTag(prefix)
        if (p.isEmpty()) {
            _state.update { it.copy(tagSuggestions = emptyList()) }
            return
        }
        viewModelScope.launch {
            val tags =
                reviews
                    .myTags(p)
                    .getOrNull()
                    .orEmpty()
                    .map { it.name }
            _state.update { s -> s.copy(tagSuggestions = tags.filter { it !in s.tags }.take(TAG_SUGGESTIONS)) }
        }
    }

    fun setTagsText(value: String) = _state.update { it.copy(tagsText = value, errors = it.errors - "tags") }

    /** Отмечает или снимает группу. */
    fun toggleGroup(groupId: String) =
        _state.update {
            it.copy(
                selected = if (groupId in it.selected) it.selected - groupId else it.selected + groupId,
                lockOpen = true,
                categoryMissing = false,
                errors = it.errors - "groups",
            )
        }

    fun setCategory(categoryId: String) = _state.update { it.copy(categoryId = categoryId, categoryMissing = false) }

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
                if (s.rating == null) "rating" to FieldErrorReason.REQUIRED else null,
                if (s.lockOpen && s.selected.isEmpty()) "groups" to FieldErrorReason.REQUIRED else null,
            ).toMap()
        val categoryId = s.categoryId
        val missing = categoryId == null || (s.selected.isNotEmpty() && s.category?.key == CATEGORY_NONE_KEY)
        if (errors.isNotEmpty() || missing) {
            _state.update { it.copy(errors = errors, categoryMissing = missing) }
            return
        }
        val groupIds = s.selected.toList()
        _state.update { it.copy(saving = true, errors = emptyMap(), categoryMissing = false, error = null) }
        viewModelScope.launch {
            val result =
                if (reviewId == null) {
                    reviews.create(CreateReviewRequest(title, description, s.rating!!, categoryId!!, tags, groupIds))
                } else {
                    val update = UpdateReviewRequest(title, description, s.rating, categoryId, tags)
                    val publish = SetPublicationsRequest(groupIds)
                    // «Без категории» допустима только у личной: закрываем замок до смены категории, открываем — после
                    if (groupIds.isEmpty()) {
                        reviews.setPublications(reviewId, publish).then { reviews.update(reviewId, update) }
                    } else {
                        reviews.update(reviewId, update).then { reviews.setPublications(reviewId, publish) }
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

private inline fun <T> Result<T>.then(next: () -> Result<T>): Result<T> = if (isSuccess) next() else this

private const val TAG_SUGGESTIONS = 8
