package app.podgon.reviews.persistence

import java.util.UUID

/** Один набор условий фильтра (панель, секция-категория, секция-фильтр); наборы складываются через «и». */
data class Criteria(
    /** Категории набора: корень выбирается вместе с подкатегориями. */
    val categoryIds: Set<UUID> = emptySet(),
    val tags: List<String> = emptyList(),
    /** `true` — достаточно любого из тегов, `false` — нужны все. */
    val anyTag: Boolean = false,
    val authorIds: Set<UUID> = emptySet(),
    val minRating: Int? = null,
    val maxRating: Int? = null,
)

/** Фильтры выборки: наборы условий и поисковый запрос (с ним выдача упорядочена по релевантности). */
data class SearchFilters(
    val criteria: List<Criteria> = emptyList(),
    val query: String? = null,
)

/**
 * Собирает общую часть запросов ленты, фасетов и оглавления. [from] — область: публикации в [groupIds],
 * по одной строке `p` на рекомендацию (`p.shared_at` — самая свежая публикация в области, `p.group_ids` —
 * группы публикации) и сама рекомендация `r`; [where] — условия фильтров (начинается с ` and`, либо пусто).
 * Для одной группы область читается по индексу ленты `(group_id, shared_at desc, review_id)`.
 */
internal class FilterSql(
    groupIds: Collection<UUID>,
    filters: SearchFilters,
) {
    val params = mutableMapOf<String, Any?>()
    val from: String
    val where: String
    val score: String

    init {
        from =
            if (groupIds.size == 1) {
                params["scope"] = groupIds.single()
                "(select review_id, shared_at, array[group_id] as group_ids from review_groups" +
                    " where group_id = :scope) p join reviews r on r.id = p.review_id"
            } else {
                params["scope"] = groupIds
                "(select review_id, max(shared_at) as shared_at," +
                    " array_agg(group_id order by shared_at desc, group_id) as group_ids" +
                    " from review_groups where group_id in (:scope) group by review_id) p" +
                    " join reviews r on r.id = p.review_id"
            }
        val sql = StringBuilder()
        filters.criteria.forEachIndexed { i, c -> append(sql, c, i) }
        var rank = "0.0"
        filters.query?.let { raw ->
            // «ё» = «е»: так же нормализованы search_vector и title_search (миграция V7)
            val q = raw.replace('ё', 'е').replace('Ё', 'Е')
            sql.append(
                " and (r.search_vector @@ websearch_to_tsquery('russian', :q)" +
                    " or r.title_search ilike :like escape '\\' or r.title_search % :q)",
            )
            params["q"] = q
            params["like"] = "%" + escapeLike(q) + "%"
            rank =
                "cast(coalesce(ts_rank_cd(r.search_vector, websearch_to_tsquery('russian', :q)), 0)" +
                " + similarity(r.title_search, :q) as float8)"
        }
        where = sql.toString()
        score = rank
    }

    private fun append(
        sql: StringBuilder,
        c: Criteria,
        i: Int,
    ) {
        if (c.categoryIds.isNotEmpty()) {
            sql.append(
                " and r.category_id in (select id from categories where id in (:cat$i) or parent_id in (:cat$i))",
            )
            params["cat$i"] = c.categoryIds
        }
        if (c.authorIds.isNotEmpty()) {
            sql.append(" and r.author_id in (:author$i)")
            params["author$i"] = c.authorIds
        }
        if (c.tags.isNotEmpty()) {
            sql.append(
                " and (select count(*) from review_tags rt join tags t on t.id = rt.tag_id" +
                    " where rt.review_id = r.id and t.name in (:tags$i)) >= :need$i",
            )
            params["tags$i"] = c.tags
            params["need$i"] = if (c.anyTag) 1 else c.tags.size
        }
        c.minRating?.let {
            sql.append(" and r.rating >= :min$i")
            params["min$i"] = it
        }
        c.maxRating?.let {
            sql.append(" and r.rating <= :max$i")
            params["max$i"] = it
        }
    }
}

internal fun escapeLike(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
