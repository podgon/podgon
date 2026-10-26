package app.podgon.mobile.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.podgon.contract.CategoryDeleteImpact
import app.podgon.contract.CategoryInfo
import app.podgon.contract.CreateCategoryRequest
import app.podgon.contract.FieldErrorReason
import app.podgon.contract.MoveCategoryRequest
import app.podgon.contract.UpdateCategoryRequest
import app.podgon.contract.Validation
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.GroupsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Категория в порядке обхода дерева и её уровень вложенности (корневая — 0). */
data class CategoryNode(
    val category: CategoryInfo,
    val depth: Int,
)

/** Удаление ждёт подтверждения: последствия известны из `delete-impact`. */
data class PendingDelete(
    val category: CategoryInfo,
    val impact: CategoryDeleteImpact,
)

data class CategoriesState(
    val loading: Boolean = true,
    val categories: List<CategoryInfo> = emptyList(),
    val error: AppError? = null,
    /** Ошибка названия в диалоге (локальная проверка). */
    val nameError: FieldErrorReason? = null,
    val pendingDelete: PendingDelete? = null,
) {
    val tree: List<CategoryNode> get() = flattenTree(categories)
}

/** Обход в глубину: родитель перед детьми, соседи в порядке, заданном сервером. Висячие узлы игнорируются. */
fun flattenTree(categories: List<CategoryInfo>): List<CategoryNode> {
    val byParent = categories.groupBy { it.parentId }
    val result = mutableListOf<CategoryNode>()

    fun visit(
        parentId: String?,
        depth: Int,
    ) {
        byParent[parentId].orEmpty().forEach {
            result += CategoryNode(it, depth)
            visit(it.id, depth + 1)
        }
    }
    visit(null, 0)
    return result
}

/** Категория и все её потомки: сюда её переносить нельзя (цикл). */
fun subtreeIds(
    categories: List<CategoryInfo>,
    rootId: String,
): Set<String> {
    val byParent = categories.groupBy { it.parentId }
    val result = mutableSetOf(rootId)
    val queue = ArrayDeque(listOf(rootId))
    while (queue.isNotEmpty()) {
        byParent[queue.removeFirst()].orEmpty().forEach {
            if (result.add(it.id)) queue += it.id
        }
    }
    return result
}

/** Дерево категорий группы; менять его может администратор (сервер проверяет права). */
class CategoriesViewModel(
    private val groupId: String,
    private val api: GroupsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = api.categories(groupId)
            _state.update {
                it.copy(loading = false, categories = result.getOrNull() ?: it.categories, error = result.appError())
            }
        }
    }

    fun create(
        name: String,
        description: String,
        parentId: String?,
        onDone: () -> Unit,
    ) = save(name, onDone) {
        api.createCategory(groupId, CreateCategoryRequest(name.trim(), description.trim(), parentId))
    }

    fun update(
        category: CategoryInfo,
        name: String,
        description: String,
        onDone: () -> Unit,
    ) = save(name, onDone) {
        api.updateCategory(groupId, category.id, UpdateCategoryRequest(name.trim(), description.trim()))
    }

    fun move(
        category: CategoryInfo,
        parentId: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = api.moveCategory(groupId, category.id, MoveCategoryRequest(parentId))
            if (result.isSuccess) {
                _state.update { it.copy(error = null) }
                load()
                onDone()
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }

    /** Узнаёт последствия удаления; диалог подтверждения показывается, когда они известны. */
    fun requestDelete(category: CategoryInfo) {
        viewModelScope.launch {
            val result = api.categoryDeleteImpact(groupId, category.id)
            result.getOrNull()?.let { impact ->
                _state.update { it.copy(error = null, pendingDelete = PendingDelete(category, impact)) }
            } ?: _state.update { it.copy(error = result.appError()) }
        }
    }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val pending = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = api.deleteCategory(groupId, pending.category.id)
            if (result.isSuccess) load() else _state.update { it.copy(error = result.appError()) }
        }
    }

    fun clearErrors() = _state.update { it.copy(nameError = null, error = null) }

    private fun save(
        name: String,
        onDone: () -> Unit,
        call: suspend () -> Result<*>,
    ) {
        val invalid = Validation.categoryName(name)
        if (invalid != null) {
            _state.update { it.copy(nameError = invalid) }
            return
        }
        viewModelScope.launch {
            val result = call()
            if (result.isSuccess) {
                _state.update { it.copy(nameError = null, error = null) }
                load()
                onDone()
            } else {
                _state.update { it.copy(error = result.appError()) }
            }
        }
    }
}
