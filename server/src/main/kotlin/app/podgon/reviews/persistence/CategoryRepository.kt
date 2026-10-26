package app.podgon.reviews.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class CategoryRow(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val description: String,
)

/** Узел шаблона с названием на выбранном языке. */
data class TemplateRow(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val description: String,
)

/** Категория поддерева и её уровень относительно корня поддерева (корень — 1). */
data class SubtreeNode(
    val id: UUID,
    val level: Int,
)

/** Категории групп и шаблон. Все запросы параметризованы. */
@Repository
class CategoryRepository(
    private val jdbc: JdbcClient,
) {
    private fun row(rs: ResultSet) =
        CategoryRow(
            id = rs.getObject("id", UUID::class.java),
            parentId = rs.getObject("parent_id", UUID::class.java),
            name = rs.getString("name"),
            description = rs.getString("description"),
        )

    /** Сериализует изменения дерева одной группы до конца транзакции (не трогая таблицы других модулей). */
    fun lockGroup(groupId: UUID) {
        jdbc
            .sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .param("key", "categories:$groupId")
            .query()
            .singleRow()
    }

    fun list(groupId: UUID): List<CategoryRow> =
        jdbc
            .sql(
                """
                select id, parent_id, name, description from categories
                where group_id = :groupId order by sort_order, lower(name), id
                """.trimIndent(),
            ).param("groupId", groupId)
            .query { rs, _ -> row(rs) }
            .list()

    fun find(
        groupId: UUID,
        id: UUID,
    ): CategoryRow? =
        jdbc
            .sql("select id, parent_id, name, description from categories where group_id = :groupId and id = :id")
            .param("groupId", groupId)
            .param("id", id)
            .query { rs, _ -> row(rs) }
            .optional()
            .orElse(null)

    fun count(groupId: UUID): Int =
        jdbc
            .sql("select count(*) from categories where group_id = :groupId")
            .param("groupId", groupId)
            .query(Int::class.java)
            .single()

    /** Глубина узла: число категорий от корня до него включительно. */
    fun depthOf(
        groupId: UUID,
        id: UUID,
    ): Int =
        jdbc
            .sql(
                """
                with recursive up as (
                    select id, parent_id, 1 as depth from categories where group_id = :groupId and id = :id
                    union all
                    select c.id, c.parent_id, up.depth + 1 from categories c
                    join up on c.id = up.parent_id where c.group_id = :groupId
                )
                select max(depth) from up
                """.trimIndent(),
            ).param("groupId", groupId)
            .param("id", id)
            .query(Int::class.java)
            .single()

    /** Узел и все его потомки с уровнями. */
    fun subtree(
        groupId: UUID,
        rootId: UUID,
    ): List<SubtreeNode> =
        jdbc
            .sql(
                """
                with recursive down as (
                    select id, 1 as level from categories where group_id = :groupId and id = :rootId
                    union all
                    select c.id, down.level + 1 from categories c
                    join down on c.parent_id = down.id where c.group_id = :groupId
                )
                select id, level from down
                """.trimIndent(),
            ).param("groupId", groupId)
            .param("rootId", rootId)
            .query { rs, _ -> SubtreeNode(rs.getObject("id", UUID::class.java), rs.getInt("level")) }
            .list()

    fun nextSortOrder(
        groupId: UUID,
        parentId: UUID?,
    ): Int =
        jdbc
            .sql(
                """
                select coalesce(max(sort_order), 0) + 1 from categories
                where group_id = :groupId and parent_id is not distinct from cast(:parentId as uuid)
                """.trimIndent(),
            ).param("groupId", groupId)
            .param("parentId", parentId)
            .query(Int::class.java)
            .single()

    /** Есть ли среди детей [parentId] (или среди корневых) категория с таким названием, кроме [exceptId]. */
    fun nameExists(
        groupId: UUID,
        parentId: UUID?,
        name: String,
        exceptId: UUID? = null,
    ): Boolean =
        jdbc
            .sql(
                """
                select exists (
                    select 1 from categories
                    where group_id = :groupId and parent_id is not distinct from cast(:parentId as uuid)
                      and lower(name) = lower(:name) and id is distinct from cast(:exceptId as uuid)
                )
                """.trimIndent(),
            ).param("groupId", groupId)
            .param("parentId", parentId)
            .param("name", name)
            .param("exceptId", exceptId)
            .query(Boolean::class.java)
            .single()

    fun insert(
        id: UUID,
        groupId: UUID,
        parentId: UUID?,
        templateId: UUID?,
        name: String,
        description: String,
        sortOrder: Int,
        createdBy: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into categories (id, group_id, parent_id, template_id, name, description, sort_order, created_by, created_at)
                values (:id, :groupId, :parentId, :templateId, :name, :description, :sortOrder, :createdBy, :now)
                """.trimIndent(),
            ).param("id", id)
            .param("groupId", groupId)
            .param("parentId", parentId)
            .param("templateId", templateId)
            .param("name", name)
            .param("description", description)
            .param("sortOrder", sortOrder)
            .param("createdBy", createdBy)
            .param("now", now.toSql())
            .update()
    }

    fun updateText(
        groupId: UUID,
        id: UUID,
        name: String,
        description: String,
    ) {
        jdbc
            .sql(
                "update categories set name = :name, description = :description where group_id = :groupId and id = :id",
            ).param("name", name)
            .param("description", description)
            .param("groupId", groupId)
            .param("id", id)
            .update()
    }

    fun updateParent(
        groupId: UUID,
        id: UUID,
        parentId: UUID?,
        sortOrder: Int,
    ) {
        jdbc
            .sql(
                "update categories set parent_id = :parentId, sort_order = :sortOrder where group_id = :groupId and id = :id",
            ).param("parentId", parentId)
            .param("sortOrder", sortOrder)
            .param("groupId", groupId)
            .param("id", id)
            .update()
    }

    /** Удаляет категорию; потомки удаляются каскадом внешнего ключа. */
    fun delete(
        groupId: UUID,
        id: UUID,
    ) {
        jdbc
            .sql("delete from categories where group_id = :groupId and id = :id")
            .param("groupId", groupId)
            .param("id", id)
            .update()
    }

    /** Шаблон с названиями на [locale]; если перевода нет — английский. Родители раньше детей. */
    fun templates(locale: String): List<TemplateRow> =
        jdbc
            .sql(
                """
                with recursive tree as (
                    select id, parent_id, sort_order, 1 as level from category_templates where parent_id is null
                    union all
                    select t.id, t.parent_id, t.sort_order, tree.level + 1 from category_templates t
                    join tree on t.parent_id = tree.id
                )
                select tree.id, tree.parent_id,
                       coalesce(l.name, e.name) as name, coalesce(l.description, e.description, '') as description
                from tree
                left join category_template_translations l on l.template_id = tree.id and l.locale = :locale
                left join category_template_translations e on e.template_id = tree.id and e.locale = 'en'
                order by tree.level, tree.sort_order
                """.trimIndent(),
            ).param("locale", locale)
            .query { rs, _ ->
                TemplateRow(
                    id = rs.getObject("id", UUID::class.java),
                    parentId = rs.getObject("parent_id", UUID::class.java),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                )
            }.list()
}
