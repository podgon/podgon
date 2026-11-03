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
    val categoryId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Публикация в группе; [kind] — тип группы (`REGULAR`, `PERSONAL`, `FAVORITES`). */
data class PublicationRow(
    val reviewId: UUID,
    val groupId: UUID,
    val kind: String,
    val sharedAt: Instant,
)

/**
 * Строка ленты или списка: рекомендация один раз (И15), [sharedAt] — самая свежая публикация в области запроса,
 * [groupIds] — группы публикации в ней.
 */
data class CardRow(
    val review: ReviewRow,
    val sharedAt: Instant,
    val groupIds: List<UUID>,
    /** Релевантность поиску (0 без запроса). */
    val score: Double = 0.0,
)

/** Позиция в результатах поиска: строки идут по убыванию `(score, sharedAt, reviewId)`. */
data class SearchPosition(
    val score: Double,
    val sharedAt: Instant,
    val reviewId: UUID,
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

/**
 * Рекомендации, публикации и теги. Все запросы параметризованы. Тип группы (`groups.kind`) читается SQL-ом
 * напрямую: публикации в системных группах («Личное», «Избранное») обрабатываются иначе, чем в обычных.
 */
@Repository
class ReviewRepository(
    private val jdbc: JdbcClient,
) {
    private val reviewColumns =
        "r.id, r.author_id, r.title, r.description, r.rating, r.category_id, r.created_at, r.updated_at"

    private fun review(rs: ResultSet) =
        ReviewRow(
            id = rs.getObject("id", UUID::class.java),
            authorId = rs.getObject("author_id", UUID::class.java),
            title = rs.getString("title"),
            description = rs.getString("description"),
            rating = rs.getInt("rating"),
            categoryId = rs.getObject("category_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )

    fun insert(row: ReviewRow) {
        jdbc
            .sql(
                """
                insert into reviews (id, author_id, title, description, rating, category_id, created_at, updated_at)
                values (:id, :author, :title, :description, :rating, :category, :now, :now)
                """.trimIndent(),
            ).param("id", row.id)
            .param("author", row.authorId)
            .param("title", row.title)
            .param("description", row.description)
            .param("rating", row.rating)
            .param("category", row.categoryId)
            .param("now", row.createdAt.toSql())
            .update()
    }

    fun update(
        id: UUID,
        title: String,
        description: String,
        rating: Int,
        categoryId: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                update reviews set title = :title, description = :description, rating = :rating,
                                   category_id = :category, updated_at = :now
                where id = :id
                """.trimIndent(),
            ).param("title", title)
            .param("description", description)
            .param("rating", rating)
            .param("category", categoryId)
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
        // разделяемая блокировка: ночная чистка тегов ([deleteUnusedTags]) не удалит тег между вставками ниже
        jdbc
            .sql(
                "select pg_advisory_xact_lock_shared(hashtextextended(:key, 0))",
            ).param("key", TAGS_LOCK)
            .query()
            .singleRow()
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

    /**
     * Удаляет теги, которые не использует ни одна рекомендация (замечание R12 ревью). Вызывать в транзакции:
     * исключительная блокировка ждёт текущие записи тегов, после неё удаление видит их результат.
     */
    fun deleteUnusedTags(): Int {
        jdbc
            .sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .param("key", TAGS_LOCK)
            .query()
            .singleRow()
        return jdbc
            .sql("delete from tags t where not exists (select 1 from review_tags rt where rt.tag_id = t.id)")
            .update()
    }

    // --- публикации ---

    fun publications(reviewId: UUID): List<PublicationRow> =
        jdbc
            .sql(
                """
                select p.review_id, p.group_id, g.kind, p.shared_at
                from review_groups p join groups g on g.id = p.group_id
                where p.review_id = :id order by p.shared_at, p.group_id
                """.trimIndent(),
            ).param("id", reviewId)
            .query { rs, _ ->
                PublicationRow(
                    reviewId = rs.getObject("review_id", UUID::class.java),
                    groupId = rs.getObject("group_id", UUID::class.java),
                    kind = rs.getString("kind"),
                    sharedAt = rs.getTimestamp("shared_at").toInstant(),
                )
            }.list()

    /** Добавляет рекомендацию в группу (избранное); повторное добавление ничего не меняет. */
    fun addPublication(
        reviewId: UUID,
        groupId: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into review_groups (review_id, group_id, shared_at) values (:review, :group, :now)
                on conflict (review_id, group_id) do nothing
                """.trimIndent(),
            ).param("review", reviewId)
            .param("group", groupId)
            .param("now", now.toSql())
            .update()
    }

    fun removePublication(
        reviewId: UUID,
        groupId: UUID,
    ) {
        jdbc
            .sql("delete from review_groups where review_id = :review and group_id = :group")
            .param("review", reviewId)
            .param("group", groupId)
            .update()
    }

    /** Какие из [reviewIds] опубликованы в группе [groupId]. */
    fun publishedIn(
        groupId: UUID,
        reviewIds: Collection<UUID>,
    ): Set<UUID> {
        if (reviewIds.isEmpty()) return emptySet()
        return jdbc
            .sql("select review_id from review_groups where group_id = :group and review_id in (:ids)")
            .param("group", groupId)
            .param("ids", reviewIds)
            .query { rs, _ -> rs.getObject("review_id", UUID::class.java) }
            .set()
    }

    /**
     * Задаёт, где рекомендация видна: в обычных группах [groupIds] либо, если набор пуст, только в личной группе
     * автора [personalGroupId] (Л1). Остальные публикации в обычных группах и в личной снимаются; у сохранённых
     * время публикации не меняется. Публикации в «Избранном» (своём и чужом) не трогаются.
     */
    fun setVisibility(
        reviewId: UUID,
        groupIds: Collection<UUID>,
        personalGroupId: UUID,
        now: Instant,
    ) {
        val keep = groupIds.ifEmpty { listOf(personalGroupId) }
        jdbc
            .sql(
                """
                delete from review_groups p using groups g
                where g.id = p.group_id and p.review_id = :id and g.kind in ('REGULAR', 'PERSONAL')
                  and p.group_id not in (:keep)
                """.trimIndent(),
            ).param("id", reviewId)
            .param("keep", keep)
            .update()
        for (groupId in keep) {
            jdbc
                .sql(
                    """
                    insert into review_groups (review_id, group_id, shared_at) values (:review, :group, :now)
                    on conflict (review_id, group_id) do nothing
                    """.trimIndent(),
                ).param("review", reviewId)
                .param("group", groupId)
                .param("now", now.toSql())
                .update()
        }
    }

    // --- списки ---

    /**
     * Рекомендации, опубликованные в [groupIds], каждая один раз. Без запроса порядок — `(sharedAt, reviewId)`
     * по убыванию (для одной группы читается по индексу ленты); с запросом сначала считается релевантность,
     * затем применяется курсор.
     */
    fun search(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
        after: SearchPosition?,
        limit: Int,
    ): List<CardRow> {
        val f = FilterSql(groupIds, filters)
        val params = f.params + mapOf("limit" to limit)
        val ranked = filters.query != null
        var keyset = ""
        var outer = ""
        val position =
            after
                ?.let {
                    mapOf("afterScore" to it.score, "afterAt" to it.sharedAt.toSql(), "afterId" to it.reviewId)
                }.orEmpty()
        if (after != null) {
            if (ranked) {
                outer = " where (x.score, x.shared_at, x.id) < (:afterScore, :afterAt, :afterId)"
            } else {
                keyset = " and (p.shared_at, p.review_id) < (:afterAt, :afterId)"
            }
        }
        val order = (if (ranked) "x.score desc, " else "") + "x.shared_at desc, x.id desc"
        val sql =
            "select x.* from (select $reviewColumns, p.shared_at, p.group_ids, ${f.score} as score" +
                " from ${f.from} where true${f.where}$keyset) x$outer order by $order limit :limit"
        return jdbc
            .sql(sql)
            .params(params + position)
            .query { rs, _ ->
                CardRow(
                    review = review(rs),
                    sharedAt = rs.getTimestamp("shared_at").toInstant(),
                    groupIds = uuids(rs, "group_ids"),
                    score = rs.getDouble("score"),
                )
            }.list()
    }

    /** Теги рекомендаций области [groupIds]: самые частые первыми, не больше [limit]; [prefix] — начало тега. */
    fun tags(
        groupIds: Collection<UUID>,
        prefix: String?,
        limit: Int,
    ): List<TagRow> {
        if (groupIds.isEmpty()) return emptyList()
        val f = FilterSql(groupIds, SearchFilters())
        return jdbc
            .sql(
                """
                select t.name, count(*) as cnt
                from ${f.from}
                join review_tags rt on rt.review_id = r.id
                join tags t on t.id = rt.tag_id
                where (cast(:prefix as text) is null or t.name ilike :like)
                group by t.name order by cnt desc, t.name limit :limit
                """.trimIndent(),
            ).params(f.params)
            .param("prefix", prefix)
            .param("like", escapeLike(prefix ?: "") + "%")
            .param("limit", limit)
            .query { rs, _ -> TagRow(rs.getString("name").lowercase(), rs.getInt("cnt")) }
            .list()
    }

    /** Групповые рекомендации автора (опубликованные хотя бы в одной обычной группе), новые сверху (И21). */
    fun byAuthor(
        authorId: UUID,
        after: Position?,
        limit: Int,
    ): List<CardRow> =
        jdbc
            .sql(
                """
                select $reviewColumns, p.shared_at, p.group_ids from reviews r
                join lateral (select max(x.shared_at) as shared_at,
                                     array_agg(x.group_id order by x.shared_at desc, x.group_id) as group_ids
                              from review_groups x join groups g on g.id = x.group_id
                              where x.review_id = r.id and g.kind = 'REGULAR') p on p.shared_at is not null
                where r.author_id = :author
                  and (cast(:afterAt as timestamptz) is null or (r.created_at, r.id) < (:afterAt, :afterId))
                order by r.created_at desc, r.id desc
                limit :limit
                """.trimIndent(),
            ).param("author", authorId)
            .param("afterAt", after?.at?.toSql())
            .param("afterId", after?.id ?: UUID(0, 0))
            .param("limit", limit)
            .query { rs, _ -> CardRow(review(rs), rs.getTimestamp("shared_at").toInstant(), uuids(rs, "group_ids")) }
            .list()
}

private const val TAGS_LOCK = "tags"

internal fun uuids(
    rs: ResultSet,
    column: String,
): List<UUID> = (rs.getArray(column).array as Array<*>).map { it as UUID }
