package app.podgon.reviews.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** Число рекомендаций и самая свежая публикация в выборке (секция ленты). */
data class SectionStats(
    val count: Int,
    val lastSharedAt: Instant,
)

/** Счётчики выборки для панели фильтров и оглавления ленты; область и условия — как у ленты ([FilterSql]). */
@Repository
class FeedRepository(
    private val jdbc: JdbcClient,
) {
    /** Число рекомендаций по категориям (точно по `category_id`, без сложения в корень). */
    fun categoryCounts(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
    ): Map<UUID, Int> {
        val f = FilterSql(groupIds, filters)
        return jdbc
            .sql("select r.category_id, count(*) as cnt from ${f.from} where true${f.where} group by r.category_id")
            .params(f.params)
            .query { rs, _ -> rs.getObject("category_id", UUID::class.java) to rs.getInt("cnt") }
            .list()
            .toMap()
    }

    fun tagCounts(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
        limit: Int,
    ): List<TagRow> {
        val f = FilterSql(groupIds, filters)
        return jdbc
            .sql(
                "select t.name, count(*) as cnt from ${f.from}" +
                    " join review_tags rt on rt.review_id = r.id join tags t on t.id = rt.tag_id" +
                    " where true${f.where} group by t.name order by cnt desc, t.name limit :limit",
            ).params(f.params)
            .param("limit", limit)
            .query { rs, _ -> TagRow(rs.getString("name").lowercase(), rs.getInt("cnt")) }
            .list()
    }

    /** Авторы выборки и число их рекомендаций, самые активные первыми. */
    fun authorCounts(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
    ): List<Pair<UUID, Int>> {
        val f = FilterSql(groupIds, filters)
        return jdbc
            .sql(
                "select r.author_id, count(*) as cnt from ${f.from} where true${f.where}" +
                    " group by r.author_id order by cnt desc, r.author_id",
            ).params(f.params)
            .query { rs, _ -> rs.getObject("author_id", UUID::class.java) to rs.getInt("cnt") }
            .list()
    }

    /** Секции корневых категорий: рекомендации подкатегорий считаются в корне. */
    fun rootSections(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
    ): Map<UUID, SectionStats> {
        val f = FilterSql(groupIds, filters)
        return jdbc
            .sql(
                "select coalesce(c.parent_id, c.id) as root, count(*) as cnt, max(p.shared_at) as last" +
                    " from ${f.from} join categories c on c.id = r.category_id where true${f.where} group by 1",
            ).params(f.params)
            .query { rs, _ ->
                rs.getObject("root", UUID::class.java) to
                    SectionStats(rs.getInt("cnt"), rs.getTimestamp("last").toInstant())
            }.list()
            .toMap()
    }

    /** Итог выборки; пустая — `null`. */
    fun stats(
        groupIds: Collection<UUID>,
        filters: SearchFilters,
    ): SectionStats? {
        val f = FilterSql(groupIds, filters)
        return jdbc
            .sql("select count(*) as cnt, max(p.shared_at) as last from ${f.from} where true${f.where}")
            .params(f.params)
            .query { rs, _ -> rs.getInt("cnt") to rs.getTimestamp("last")?.toInstant() }
            .single()
            .let { (count, last) -> last?.let { SectionStats(count, it) } }
    }

    /** Число рекомендаций, опубликованных в каждой из групп. */
    fun reviewCounts(groupIds: Collection<UUID>): Map<UUID, Int> {
        if (groupIds.isEmpty()) return emptyMap()
        return jdbc
            .sql("select group_id, count(*) as cnt from review_groups where group_id in (:ids) group by group_id")
            .param("ids", groupIds)
            .query { rs, _ -> rs.getObject("group_id", UUID::class.java) to rs.getInt("cnt") }
            .list()
            .toMap()
    }

    /** Существующие теги из [names]: удалённые из словаря в сохранённом фильтре игнорируются. */
    fun existingTags(names: Collection<String>): Set<String> {
        if (names.isEmpty()) return emptySet()
        return jdbc
            .sql("select name from tags where name in (:names)")
            .param("names", names)
            .query { rs, _ -> rs.getString("name").lowercase() }
            .set()
    }
}
