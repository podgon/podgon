package app.podgon.reviews.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** Как правило переноса отбирает публикации; все наборы заданы заранее и проверены сервисом. */
enum class ShareSelector { ALL, IN_CATEGORIES, NOT_IN_CATEGORIES, WITH_TAGS, WITHOUT_TAGS }

/** Отобранная рекомендация и категория, подобранная для неё в новой группе (если есть). */
data class ShareCandidate(
    val reviewId: UUID,
    val title: String,
    /** Название категории в исходной группе; `null` — рекомендация личная. */
    val sourceCategory: String?,
    val targetCategoryId: UUID?,
)

data class TargetCategory(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val description: String,
)

/** Запросы переноса рекомендаций в новую группу. Категории читаются SQL-ом напрямую (как и в поиске). */
@Repository
class SharingRepository(
    private val jdbc: JdbcClient,
) {
    /**
     * Рекомендации пользователя, ещё не опубликованные в [targetGroupId], которые подходят правилу.
     * Правило проверяется по каждой исходной публикации (в группах [sourceGroupIds]); для рекомендации
     * с несколькими подходящими публикациями берётся та, у которой категория подбирается по шаблону,
     * затем самая ранняя.
     */
    fun candidates(
        userId: UUID,
        targetGroupId: UUID,
        sourceGroupIds: Collection<UUID>,
        selector: ShareSelector,
        categoryIds: Collection<UUID>,
        withSubcategories: Boolean,
        tags: Collection<String>,
    ): List<ShareCandidate> {
        val params = mutableMapOf<String, Any?>("user" to userId, "target" to targetGroupId)
        val with =
            if (selector == ShareSelector.IN_CATEGORIES || selector == ShareSelector.NOT_IN_CATEGORIES) {
                params["categories"] = categoryIds
                params["sources"] = sourceGroupIds
                val descend =
                    if (withSubcategories) {
                        " union all select c.id from categories c join tree t on c.parent_id = t.id" +
                            " where c.group_id in (:sources)"
                    } else {
                        ""
                    }
                "with recursive tree as (select id from categories where id in (:categories)" +
                    " and group_id in (:sources)$descend) "
            } else {
                ""
            }
        val withTags =
            "exists (select 1 from review_tags rt join tags t on t.id = rt.tag_id" +
                " where rt.review_id = r.id and t.name in (:tags))"
        val predicate =
            when (selector) {
                ShareSelector.ALL -> "true"
                ShareSelector.IN_CATEGORIES -> "sg.category_id in (select id from tree)"
                ShareSelector.NOT_IN_CATEGORIES ->
                    "(sg.category_id is null or sg.category_id not in (select id from tree))"
                ShareSelector.WITH_TAGS -> withTags
                ShareSelector.WITHOUT_TAGS -> "not $withTags"
            }
        if (selector == ShareSelector.WITH_TAGS || selector == ShareSelector.WITHOUT_TAGS) params["tags"] = tags
        val sql =
            with +
                """
                select x.review_id, x.title, x.source_name, x.target_category from (
                    select distinct on (r.id) r.id as review_id, r.title, sc.name as source_name, tc.id as target_category
                    from reviews r
                    left join review_groups sg on sg.review_id = r.id and sg.group_id <> :target
                    left join categories sc on sc.id = sg.category_id
                    left join lateral (
                        select c.id from categories c where c.group_id = :target and c.template_id = sc.template_id limit 1
                    ) tc on sc.template_id is not null
                    where r.author_id = :user
                      and not exists (select 1 from review_groups t where t.review_id = r.id and t.group_id = :target)
                      and $predicate
                    order by r.id, (tc.id is null), sg.shared_at nulls last
                ) x
                order by x.title, x.review_id
                """.trimIndent()
        return jdbc
            .sql(sql)
            .params(params)
            .query { rs, _ ->
                ShareCandidate(
                    reviewId = rs.getObject("review_id", UUID::class.java),
                    title = rs.getString("title"),
                    sourceCategory = rs.getString("source_name"),
                    targetCategoryId = rs.getObject("target_category", UUID::class.java),
                )
            }.list()
    }

    fun categoriesOf(groupId: UUID): List<TargetCategory> =
        jdbc
            .sql(
                """
                select id, parent_id, name, description from categories
                where group_id = :g order by sort_order, lower(name), id
                """.trimIndent(),
            ).param("g", groupId)
            .query { rs, _ ->
                TargetCategory(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("parent_id", UUID::class.java),
                    rs.getString("name"),
                    rs.getString("description"),
                )
            }.list()

    /** Публикует рекомендации в группе; уже опубликованные (гонка с ручной публикацией) пропускает. */
    fun publish(
        groupId: UUID,
        items: List<Pair<UUID, UUID>>,
        now: Instant,
    ) {
        for ((reviewId, categoryId) in items) {
            jdbc
                .sql(
                    """
                    insert into review_groups (review_id, group_id, category_id, shared_at)
                    values (:review, :group, :category, :now)
                    on conflict (review_id, group_id) do nothing
                    """.trimIndent(),
                ).param("review", reviewId)
                .param("group", groupId)
                .param("category", categoryId)
                .param("now", now.toSql())
                .update()
        }
    }

    // --- выход, удаление группы и аккаунта ---

    fun countAuthorPublications(
        groupId: UUID,
        authorId: UUID,
    ): Int =
        jdbc
            .sql(
                """
                select count(*) from review_groups g join reviews r on r.id = g.review_id
                where g.group_id = :g and r.author_id = :a
                """.trimIndent(),
            ).param("g", groupId)
            .param("a", authorId)
            .query(Int::class.java)
            .single()

    fun deleteAuthorPublications(
        groupId: UUID,
        authorId: UUID,
    ) {
        jdbc
            .sql(
                """
                delete from review_groups g using reviews r
                where r.id = g.review_id and g.group_id = :g and r.author_id = :a
                """.trimIndent(),
            ).param("g", groupId)
            .param("a", authorId)
            .update()
    }

    fun countGroupPublications(groupId: UUID): Int =
        jdbc
            .sql("select count(*) from review_groups where group_id = :g")
            .param("g", groupId)
            .query(Int::class.java)
            .single()

    private val exclusive =
        """
        from reviews r
        where exists (select 1 from review_groups g where g.review_id = r.id and g.group_id = :g)
          and not exists (select 1 from review_groups o where o.review_id = r.id and o.group_id <> :g)
        """.trimIndent()

    /** Рекомендации, опубликованные только в этой группе. */
    fun countExclusive(groupId: UUID): Int =
        jdbc
            .sql("select count(*) $exclusive")
            .param("g", groupId)
            .query(Int::class.java)
            .single()

    fun deleteExclusive(groupId: UUID) {
        jdbc.sql("delete from reviews where id in (select r.id $exclusive)").param("g", groupId).update()
    }

    fun countPublished(authorId: UUID): Int =
        jdbc
            .sql(
                """
                select count(*) from reviews r
                where r.author_id = :a and exists (select 1 from review_groups g where g.review_id = r.id)
                """.trimIndent(),
            ).param("a", authorId)
            .query(Int::class.java)
            .single()

    /** Рекомендации с публикациями переходят другому автору (служебному пользователю). */
    fun reassignPublished(
        fromAuthor: UUID,
        toAuthor: UUID,
    ) {
        jdbc
            .sql(
                """
                update reviews r set author_id = :to
                where r.author_id = :from and exists (select 1 from review_groups g where g.review_id = r.id)
                """.trimIndent(),
            ).param("from", fromAuthor)
            .param("to", toAuthor)
            .update()
    }

    /** Все рекомендации автора (публикации и теги уходят каскадом). */
    fun deleteAllOf(authorId: UUID) {
        jdbc.sql("delete from reviews where author_id = :a").param("a", authorId).update()
    }
}
