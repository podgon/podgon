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

    /** Лента группы: публикации по убыванию времени; [after] — последняя строка предыдущей страницы. */
    fun feed(
        groupId: UUID,
        after: Position?,
        limit: Int,
    ): List<CardRow> =
        jdbc
            .sql(
                """
                select $reviewColumns, g.category_id, g.shared_at
                from review_groups g join reviews r on r.id = g.review_id
                where g.group_id = :group
                  and (cast(:afterAt as timestamptz) is null or (g.shared_at, g.review_id) < (:afterAt, :afterId))
                order by g.shared_at desc, g.review_id desc
                limit :limit
                """.trimIndent(),
            ).param("group", groupId)
            .param("afterAt", after?.at?.toSql())
            .param("afterId", after?.id ?: UUID(0, 0))
            .param("limit", limit)
            .query { rs, _ ->
                CardRow(
                    review(rs),
                    rs.getObject("category_id", UUID::class.java),
                    rs.getTimestamp("shared_at").toInstant(),
                )
            }.list()

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
