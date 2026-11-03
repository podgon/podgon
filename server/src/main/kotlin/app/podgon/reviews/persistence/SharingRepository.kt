package app.podgon.reviews.persistence

import app.podgon.common.toSql
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** Как правило переноса отбирает публикации; все наборы заданы заранее и проверены сервисом. */
enum class ShareSelector { ALL, IN_CATEGORIES, NOT_IN_CATEGORIES, WITH_TAGS, WITHOUT_TAGS }

/** Рекомендация, отобранная правилом переноса. */
data class ShareCandidate(
    val reviewId: UUID,
    val title: String,
    val categoryId: UUID,
)

/**
 * Перенос рекомендаций в новую группу, выход, удаление группы и аккаунта. Тип групп (`groups.kind`) и членство
 * (`memberships`) читаются SQL-ом напрямую: правила «Личного» и «Избранного» (Л1, Ф1) требуют их в одном запросе
 * с публикациями.
 */
@Repository
class SharingRepository(
    private val jdbc: JdbcClient,
) {
    /**
     * Групповые рекомендации пользователя (опубликованные хотя бы в одной обычной группе; личные не переносятся),
     * ещё не опубликованные в [targetGroupId], которые подходят правилу. Категории — набора: корень включает
     * подкатегории.
     */
    fun candidates(
        userId: UUID,
        targetGroupId: UUID,
        selector: ShareSelector,
        categoryIds: Collection<UUID>,
        tags: Collection<String>,
    ): List<ShareCandidate> {
        val params = mutableMapOf<String, Any?>("user" to userId, "target" to targetGroupId)
        val inCategories =
            "r.category_id in (select id from categories where id in (:categories) or parent_id in (:categories))"
        val withTags =
            "exists (select 1 from review_tags rt join tags t on t.id = rt.tag_id" +
                " where rt.review_id = r.id and t.name in (:tags))"
        val predicate =
            when (selector) {
                ShareSelector.ALL -> "true"
                ShareSelector.IN_CATEGORIES -> inCategories
                ShareSelector.NOT_IN_CATEGORIES -> "not $inCategories"
                ShareSelector.WITH_TAGS -> withTags
                ShareSelector.WITHOUT_TAGS -> "not $withTags"
            }
        if (selector == ShareSelector.IN_CATEGORIES || selector == ShareSelector.NOT_IN_CATEGORIES) {
            params["categories"] = categoryIds
        }
        if (selector == ShareSelector.WITH_TAGS || selector == ShareSelector.WITHOUT_TAGS) params["tags"] = tags
        val sql =
            """
            select r.id, r.title, r.category_id from reviews r
            where r.author_id = :user
              and exists (select 1 from review_groups p join groups g on g.id = p.group_id
                          where p.review_id = r.id and g.kind = 'REGULAR')
              and not exists (select 1 from review_groups t where t.review_id = r.id and t.group_id = :target)
              and $predicate
            order by r.title, r.id
            """.trimIndent()
        return jdbc
            .sql(sql)
            .params(params)
            .query { rs, _ ->
                ShareCandidate(
                    reviewId = rs.getObject("id", UUID::class.java),
                    title = rs.getString("title"),
                    categoryId = rs.getObject("category_id", UUID::class.java),
                )
            }.list()
    }

    /** Публикует рекомендации в группе; уже опубликованные (гонка с ручной публикацией) пропускает. */
    fun publish(
        groupId: UUID,
        reviewIds: List<UUID>,
        now: Instant,
    ) {
        for (reviewId in reviewIds) {
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

    // публикации в «Избранном» не в счёт: это закладки, а не место, где рекомендация видна
    private val exclusive =
        """
        from reviews r
        where exists (select 1 from review_groups g where g.review_id = r.id and g.group_id = :g)
          and not exists (select 1 from review_groups o join groups og on og.id = o.group_id
                          where o.review_id = r.id and o.group_id <> :g and og.kind <> 'FAVORITES')
        """.trimIndent()

    /** Рекомендации, опубликованные (вне «Избранного») только в этой группе. */
    fun countExclusive(groupId: UUID): Int =
        jdbc
            .sql("select count(*) $exclusive")
            .param("g", groupId)
            .query(Int::class.java)
            .single()

    fun deleteExclusive(groupId: UUID) {
        jdbc.sql("delete from reviews where id in (select r.id $exclusive)").param("g", groupId).update()
    }

    /**
     * Рекомендации, которые были только в этой группе, возвращаются авторам в «Личное». У кого личной группы нет
     * («удалённый пользователь»), их больше никто не увидит — они удаляются.
     */
    fun returnExclusiveToPersonal(
        groupId: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into review_groups (review_id, group_id, shared_at)
                select r.id, pg.id, :now
                from reviews r join groups pg on pg.created_by = r.author_id and pg.kind = 'PERSONAL'
                where r.id in (select r.id $exclusive)
                """.trimIndent(),
            ).param("g", groupId)
            .param("now", now.toSql())
            .update()
        deleteExclusive(groupId)
    }

    /** Рекомендации автора, которые нигде не видны (кроме «Избранного»), становятся личными (Л1). */
    fun makeOrphansPersonal(
        authorId: UUID,
        now: Instant,
    ) {
        jdbc
            .sql(
                """
                insert into review_groups (review_id, group_id, shared_at)
                select r.id, pg.id, :now
                from reviews r join groups pg on pg.created_by = r.author_id and pg.kind = 'PERSONAL'
                where r.author_id = :a
                  and not exists (select 1 from review_groups o join groups og on og.id = o.group_id
                                  where o.review_id = r.id and og.kind <> 'FAVORITES')
                """.trimIndent(),
            ).param("a", authorId)
            .param("now", now.toSql())
            .update()
    }

    /**
     * Ф1: чужая рекомендация убирается из «Избранного», когда она больше не опубликована ни в одной обычной группе,
     * где владелец избранного — участник. Проверяются только заданные срезы: записи избранного [owner], рекомендация
     * [reviewId], рекомендации автора [author], рекомендации, опубликованные в [groupId] (публикация в ней при этом
     * не в счёт — группа удаляется). Свои рекомендации в своём избранном остаются.
     */
    fun cleanupFavorites(
        owner: UUID? = null,
        reviewId: UUID? = null,
        author: UUID? = null,
        groupId: UUID? = null,
    ) {
        jdbc
            .sql(
                """
                delete from review_groups f using groups fg, reviews r
                where fg.id = f.group_id and fg.kind = 'FAVORITES' and r.id = f.review_id and r.author_id <> fg.created_by
                  and (cast(:owner as uuid) is null or fg.created_by = :owner)
                  and (cast(:review as uuid) is null or f.review_id = :review)
                  and (cast(:author as uuid) is null or r.author_id = :author)
                  and (cast(:group as uuid) is null
                       or exists (select 1 from review_groups x where x.review_id = f.review_id and x.group_id = :group))
                  and not exists (
                      select 1 from review_groups p
                      join groups pg on pg.id = p.group_id and pg.kind = 'REGULAR'
                      join memberships m on m.group_id = p.group_id and m.user_id = fg.created_by
                      where p.review_id = f.review_id and p.group_id is distinct from cast(:group as uuid))
                """.trimIndent(),
            ).param("owner", owner)
            .param("review", reviewId)
            .param("author", author)
            .param("group", groupId)
            .update()
    }

    private val inRegularGroup =
        "exists (select 1 from review_groups g join groups k on k.id = g.group_id where g.review_id = r.id and k.kind = 'REGULAR')"

    /** Групповые рекомендации автора (опубликованные хотя бы в одной обычной группе). */
    fun countPublished(authorId: UUID): Int =
        jdbc
            .sql(
                """
                select count(*) from reviews r
                where r.author_id = :a and $inRegularGroup
                """.trimIndent(),
            ).param("a", authorId)
            .query(Int::class.java)
            .single()

    /** Групповые рекомендации переходят другому автору (служебному пользователю). */
    fun reassignPublished(
        fromAuthor: UUID,
        toAuthor: UUID,
    ) {
        jdbc
            .sql(
                """
                update reviews r set author_id = :to
                where r.author_id = :from and $inRegularGroup
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
