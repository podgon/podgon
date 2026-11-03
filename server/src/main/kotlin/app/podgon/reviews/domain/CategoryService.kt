package app.podgon.reviews.domain

import app.podgon.contract.Category
import app.podgon.contract.CategorySet
import app.podgon.reviews.persistence.CategoryRepository
import app.podgon.reviews.persistence.CategoryRow
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Как категорию можно использовать в рекомендации. */
enum class CategoryUse {
    /** Есть в наборе и не скрыта. */
    OK,

    /** «Без категории»: только для личной рекомендации (И28). */
    PERSONAL_ONLY,

    /** Нет в наборе или скрыта (`archived`). */
    UNKNOWN,
}

/**
 * Общий набор категорий. Меняется только миграцией (то есть с перезапуском сервера), поэтому читается из БД
 * один раз на язык и хранится в памяти; версия набора — хеш содержимого, она же `ETag` для клиента.
 */
@Service
class CategoryService(
    private val categories: CategoryRepository,
) {
    private val sets = ConcurrentHashMap<String, CategorySet>()
    private val byId by lazy { categories.list("en").associateBy { it.id } }

    fun set(locale: String): CategorySet = sets.computeIfAbsent(locale) { load(it) }

    fun use(id: UUID): CategoryUse {
        val row = byId[id] ?: return CategoryUse.UNKNOWN
        return when {
            row.archived -> CategoryUse.UNKNOWN
            row.system -> CategoryUse.PERSONAL_ONLY
            else -> CategoryUse.OK
        }
    }

    fun isSystem(id: UUID): Boolean = byId[id]?.system == true

    private fun load(locale: String): CategorySet {
        val items = categories.list(locale).map { it.toDto() }
        val digest = MessageDigest.getInstance("SHA-256").digest(items.toString().toByteArray())
        val version = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(VERSION_BYTES))
        return CategorySet(version, items)
    }

    private fun CategoryRow.toDto() =
        Category(id.toString(), key, parentId?.toString(), name, sortOrder, system, archived)

    private companion object {
        const val VERSION_BYTES = 12
    }
}
