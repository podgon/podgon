package app.podgon.mobile.feature.reviews

import app.podgon.contract.Category
import app.podgon.contract.CategorySet
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.network.CategoriesApi

/** Категория в дереве: [depth] 0 — корень, 1 — подкатегория. */
data class CategoryNode(
    val category: Category,
    val depth: Int,
)

/** Дерево набора в порядке показа: корень, затем его подкатегории. Скрытые категории не предлагаются. */
fun flattenTree(categories: List<Category>): List<CategoryNode> {
    val visible = categories.filter { !it.archived }
    val byParent = visible.groupBy { it.parentId }
    return byParent[null].orEmpty().flatMap { root ->
        listOf(CategoryNode(root, 0)) + byParent[root.id].orEmpty().map { CategoryNode(it, 1) }
    }
}

/**
 * Набор категорий с кэшем: сохранённый набор сверяется с сервером по версии (`ETag`), без сети
 * используется сохранённый. Смена языка профиля даёт другую версию — набор приходит заново.
 */
class CategoryRepository(
    private val api: CategoriesApi,
    private val cache: ResponseCache,
) {
    suspend fun set(): Result<CategorySet> {
        val saved = cache.read<CategorySet>(KEY)
        return api.categorySet(saved?.version).fold(
            onSuccess = { fresh ->
                fresh?.let { cache.write(KEY, it) }
                // 304 приходит, только если версия передана, то есть набор сохранён
                Result.success(fresh ?: saved!!)
            },
            onFailure = { if (saved != null) Result.success(saved) else Result.failure(it) },
        )
    }

    private companion object {
        const val KEY = "categories"
    }
}
