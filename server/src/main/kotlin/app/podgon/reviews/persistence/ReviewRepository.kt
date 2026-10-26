package app.podgon.reviews.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class ReviewRow(
    val id: UUID,
    val authorId: UUID,
    val title: String,
    val description: String,
    val rating: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PublicationRow(
    val reviewId: UUID,
    val groupId: UUID,
    val categoryId: UUID,
    val sharedAt: Instant,
)

/** Строка ленты или списка: рекомендация и (для ленты) её публикация в группе. */
data class CardRow(
    val review: ReviewRow,
    val categoryId: UUID?,
    val sharedAt: Instant?,
    val groupId: UUID? = null,
    /** Релевантность поиску (0 без запроса). */
    val score: Double = 0.0,
)

/** Фильтры поиска публикаций; все заданные условия применяются через «и». */
data class SearchFilters(
    /** Категории: выбираются вместе с подкатегориями. */
    val categoryIds: Set<UUID> = emptySet(),
    val tags: List<String> = emptyList(),
    /** `true` — достаточно любого из тегов, `false` — нужны все. */
    val anyTag: Boolean = false,
    val authorId: UUID? = null,
    /** Поисковый запрос; с ним выдача упорядочена по релевантности. */
    val query: String? = null,
)

/** Позиция в результатах поиска: строки идут по убыванию `(score, sharedAt, reviewId, groupId)`. */
data class SearchPosition(
    val score: Double,
    val sharedAt: Instant,
    val reviewId: UUID,
    val groupId: UUID,
)

/** Тег и число публикаций с ним. */
data class TagRow(
    val name: String,
    val count: Int,
)

/** Позиция keyset-пагинации: время и идентификатор последней строки предыдущей страницы. */
data class Position(
    val at: Instant,
    val id: UUID,
)

/** Рекомендации, публикации и теги. Все запросы параметризованы. */
@Repository
class ReviewRepository(
    private val jdbc: JdbcClient,
) {
    private val reviewColumns = "r.id, r.author_id, r.title, r.description, r.rating, r.created_at, r.updated_at"

    private fun review(rs: ResultSet) =
        ReviewRow(
            id = rs.getObject("id", UUID::class.java),
            authorId = rs.getObject("author_id", UUID::class.java),
            title = rs.getString("title"),
            description = rs.getString("description"),
            rating = rs.getInt("rating"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )

    fun insert(row: ReviewRow) {
        jdbc
            .sql(
                """
                insert into reviews (id, author_id, title, description, rating, created_at, updated_at)
                values (:id, :author, :title, :description, :rating, :now, :now)
                """.trimIndent(),
            ).param("id", row.id)
            .param("author", row.authorId)
            .param("title", row.title)
            .param("description", row.description)
            .param("rating", row.rating)
            .param("now", row.createdAt.toSql())
            .update()
    }

    fun update(
        id: UUID,
        title: String,
        description: String,
        rating: Int,
        now: Instant,
    ) {
        jdbc
            .sql(
                "update reviews set title = :title, description = :description, rating = :rating, updated_at = :now where id = :id",
            ).param("title", title)
            .param("description", description)
            .param("rating", rating)
            .param("now", now.toSql())
            .param("id", id)
            .update()
    }

    fun find(id: UUID): ReviewRow? =
        jdbc
            .sql("select $reviewColumns from reviews r where r.id = :id")
            .param("id", id)
            .query { rs, _ -> review(rs) }
            .optional()
            .orElse(null)

    fun delete(id: UUID) {
        jdbc.sql("delete from reviews where id = :id").param("id", id).update()
    }

    fun countByAuthor(authorId: UUID): Int =
        jdbc
            .sql("select count(*) from reviews where author_id = :author")
            .param("author", authorId)
            .query(Int::class.java)
            .single()

    /** Сериализует изменения одной рекомендации до конца транзакции. */
    fun lock(id: UUID) {
        jdbc
            .sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .param("key", "review:$id")
            .query()
            .singleRow()
    }

    // --- теги ---

    fun tagsOf(reviewIds: Collection<UUID>): Map<UUID, List<String>> {
        if (reviewIds.isEmpty()) return emptyMap()
        return jdbc
            .sql(
                """
                select rt.review_id, t.name from review_tags rt join tags t on t.id = rt.tag_id
                where rt.review_id in (:ids) order by t.name
                """.trimIndent(),
            ).param("ids", reviewIds)
            .query { rs, _ -> rs.getObject("review_id", UUID::class.java) to rs.getString("name").lowercase() }
            .list()
            .groupBy({ it.first }, { it.second })
    }

    fun replaceTags(
        reviewId: UUID,
        tags: List<String>,
    ) {
        jdbc.sql("delete from review_tags where review_id = :id").param("id", reviewId).update()
        for (tag in tags) {
            jdbc.sql("insert into tags (name) values (:name) on conflict (name) do nothing").param("name", tag).update()
            jdbc
                .sql("insert into review_tags (review_id, tag_id) select :id, id from tags where name = :name")
                .param("id", reviewId)
                .param("name", tag)
                .update()
        }
    }

    // --- публикации ---

    fun publications(reviewId: UUID): List<PublicationRow> =
        jdbc
            .sql(
                "select review_id, group_id, category_id, shared_at from review_groups where review_id = :id order by shared_at, group_id",
            ).param("id", reviewId)
            .query { rs, _ ->
                PublicationRow(
                    reviewId = rs.getObject("review_id", UUID::class.java),
                    groupId = rs.getObject("group_id", UUID::class.java),
                    categoryId = rs.getObject("category_id", UUID::class.java),
                    sharedAt = rs.getTimestamp("shared_at").toInstant(),
                )
            }.list()

    /** Снимает публикации в группах, которых нет в [keepGroupIds]. */
    fun removePublicationsExcept(
        reviewId: UUID,
        keepGroupIds: Collection<UUID>,
    ) {
        if (keepGroupIds.isEmpty()) {
            jdbc.sql("delete from review_groups where review_id = :id").param("id", reviewId).update()
        } else {
            jdbc
                .sql("delete from review_groups where review_id = :id and group_id not in (:keep)")
                .param("id", reviewId)
                .param("keep", keepGroupIds)
                .update()
        }
    }

    /** Новая публикация либо смена категории существующей (время публикации не меняется). */
    fun upsertPublication(
        reviewId: UUID,
        groupId: UUID,
        categoryId: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into review_groups (review_id, group_id, category_id, shared_at)
                values (:review, :group, :category, :now)
                on conflict (review_id, group_id) do update set category_id = excluded.category_id
                """.trimIndent(),
            ).param("review", reviewId)
            .param("group", groupId)
            .param("category", categoryId)
            .param("now", now.toSql())
            .update()
    }

    // --- списки ---

    /**
     * Поиск публикаций в [groupIds]. Без запроса порядок — `(sharedAt, reviewId[, groupId])` по убыванию и
     * читается по индексу ленты; с запросом сначала считается релевантность, затем применяется курсор.
     */
    fun search(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
        after: SearchPosition?,
        limit: Int,
    ): List<CardRow> {
        val params = mutableMapOf<String, Any?>("groups" to groupIds, "limit" to limit)
        val ranked = filters.query != null
        val inner = StringBuilder()
        val with = StringBuilder()
        if (filters.categoryIds.isNotEmpty()) {
            with.append(
                """
                with recursive tree as (
                    select id from categories where id in (:categories) and group_id in (:groups)
                    union all
                    select c.id from categories c join tree t on c.parent_id = t.id where c.group_id in (:groups)
                )
                """.trimIndent() + "\n",
            )
            params["categories"] = filters.categoryIds
            inner.append(" and g.category_id in (select id from tree)")
        }
        filters.authorId?.let {
            inner.append(" and r.author_id = :author")
            params["author"] = it
        }
        if (filters.tags.isNotEmpty()) {
            inner.append(
                " and (select count(*) from review_tags rt join tags t on t.id = rt.tag_id" +
                    " where rt.review_id = r.id and t.name in (:tags)) >= :tagsNeeded",
            )
            params["tags"] = filters.tags
            params["tagsNeeded"] = if (filters.anyTag) 1 else filters.tags.size
        }
        var score = "0.0"
        filters.query?.let {
            inner.append(
                " and (r.search_vector @@ websearch_to_tsquery('russian', :q)" +
                    " or r.title ilike :like escape '\\' or r.title % :q)",
            )
            params["q"] = it
            params["like"] = "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            score =
                "cast(coalesce(ts_rank_cd(r.search_vector, websearch_to_tsquery('russian', :q)), 0)" +
                " + similarity(r.title, :q) as float8)"
        }
        val single = groupIds.size == 1
        val outer = StringBuilder()
        if (after != null) {
            params["afterScore"] = after.score
            params["afterAt"] = after.sharedAt.toSql()
            params["afterId"] = after.reviewId
            params["afterGroup"] = after.groupId
            if (ranked) {
                outer.append(
                    " where (x.score, x.shared_at, x.id, x.group_id) < (:afterScore, :afterAt, :afterId, :afterGroup)",
                )
            } else if (single) {
                inner.append(" and (g.shared_at, g.review_id) < (:afterAt, :afterId)")
            } else {
                inner.append(" and (g.shared_at, g.review_id, g.group_id) < (:afterAt, :afterId, :afterGroup)")
            }
        }
        val order =
            (if (ranked) "x.score desc, " else "") + "x.shared_at desc, x.id desc" +
                (if (single) "" else ", x.group_id desc")
        val sql =
            with.toString() +
                "select x.* from (select $reviewColumns, g.group_id, g.category_id, g.shared_at, $score as score" +
                " from review_groups g join reviews r on r.id = g.review_id" +
                " where g.group_id in (:groups)$inner) x$outer order by $order limit :limit"
        return jdbc
            .sql(sql)
            .params(params)
            .query { rs, _ ->
                CardRow(
                    review = review(rs),
                    categoryId = rs.getObject("category_id", UUID::class.java),
                    sharedAt = rs.getTimestamp("shared_at").toInstant(),
                    groupId = rs.getObject("group_id", UUID::class.java),
                    score = rs.getDouble("score"),
                )
            }.list()
    }

    /** Теги публикаций группы: самые частые первыми, не больше [limit]; [prefix] — начало тега. */
    fun groupTags(
        groupId: UUID,
        prefix: String?,
        limit: Int,
    ): List<TagRow> =
        jdbc
            .sql(
                """
                select t.name, count(*) as cnt
                from review_groups g
                join review_tags rt on rt.review_id = g.review_id
                join tags t on t.id = rt.tag_id
                where g.group_id = :group and (cast(:prefix as text) is null or t.name ilike :like)
                group by t.name order by cnt desc, t.name limit :limit
                """.trimIndent(),
            ).param("group", groupId)
            .param("prefix", prefix)
            .param("like", (prefix ?: "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
            .param("limit", limit)
            .query { rs, _ -> TagRow(rs.getString("name").lowercase(), rs.getInt("cnt")) }
            .list()

    fun byAuthor(
        authorId: UUID,
        after: Position?,
        limit: Int,
    ): List<CardRow> =
        jdbc
            .sql(
                """
                select $reviewColumns from reviews r
                where r.author_id = :author
                  and (cast(:afterAt as timestamptz) is null or (r.created_at, r.id) < (:afterAt, :afterId))
                order by r.created_at desc, r.id desc
                limit :limit
                """.trimIndent(),
            ).param("author", authorId)
            .param("afterAt", after?.at?.toSql())
            .param("afterId", after?.id ?: UUID(0, 0))
            .param("limit", limit)
            .query { rs, _ -> CardRow(review(rs), null, null) }
            .list()

    // --- для категорий ---

    fun countPublications(
        groupId: UUID,
        categoryIds: Collection<UUID>,
    ): Int =
        if (categoryIds.isEmpty()) {
            0
        } else {
            jdbc
                .sql("select count(*) from review_groups where group_id = :group and category_id in (:ids)")
                .param("group", groupId)
                .param("ids", categoryIds)
                .query(Int::class.java)
                .single()
        }

    fun countPublicationsByOthers(
        groupId: UUID,
        categoryIds: Collection<UUID>,
        userId: UUID,
    ): Int =
        if (categoryIds.isEmpty()) {
            0
        } else {
            jdbc
                .sql(
                    """
                    select count(*) from review_groups g join reviews r on r.id = g.review_id
                    where g.group_id = :group and g.category_id in (:ids) and r.author_id <> :user
                    """.trimIndent(),
                ).param("group", groupId)
                .param("ids", categoryIds)
                .param("user", userId)
                .query(Int::class.java)
                .single()
        }
}
