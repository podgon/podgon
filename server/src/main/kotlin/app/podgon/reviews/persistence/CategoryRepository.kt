package app.podgon.reviews.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/** Категория набора с названием на выбранном языке. */
data class CategoryRow(
    val id: UUID,
    val key: String,
    val parentId: UUID?,
    val name: String,
    val sortOrder: Int,
    val system: Boolean,
    val archived: Boolean,
)

/** Общий набор категорий (ADR-011; наполняется миграциями). Все запросы параметризованы. */
@Repository
class CategoryRepository(
    private val jdbc: JdbcClient,
) {
    /** Весь набор на [locale]; если перевода нет — английский, затем ключ. */
    fun list(locale: String): List<CategoryRow> =
        jdbc
            .sql(
                """
                select c.id, c.key, c.parent_id, c.sort_order, c.system, c.archived,
                       coalesce(l.name, e.name, c.key) as name
                from categories c
                left join category_names l on l.category_id = c.id and l.locale = :locale
                left join category_names e on e.category_id = c.id and e.locale = 'en'
                where c.group_id is null
                order by c.sort_order, c.id
                """.trimIndent(),
            ).param("locale", locale)
            .query { rs, _ ->
                CategoryRow(
                    id = rs.getObject("id", UUID::class.java),
                    key = rs.getString("key"),
                    parentId = rs.getObject("parent_id", UUID::class.java),
                    name = rs.getString("name"),
                    sortOrder = rs.getInt("sort_order"),
                    system = rs.getBoolean("system"),
                    archived = rs.getBoolean("archived"),
                )
            }.list()
}
