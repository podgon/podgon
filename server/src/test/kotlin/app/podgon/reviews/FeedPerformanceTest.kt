package app.podgon.reviews

import app.podgon.groups.GroupsTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Нагрузочный smoke ленты: на большом наборе запрос идёт по индексу ленты, без сортировки всей группы. */
class FeedPerformanceTest : GroupsTestBase() {
    @Test
    fun `лента группы на тысячах публикаций читается по индексу`() {
        val author = newUser("perf_author")
        val groupId = createGroup(author)["id"].asString()
        val categoryId = body(get("/groups/$groupId/categories", author))[0]["id"].asString()
        val authorId = userId(author)

        jdbc
            .sql(
                """
                insert into reviews (id, author_id, title, description, rating, created_at, updated_at)
                select gen_random_uuid(), :author, 'Рекомендация ' || n, 'Описание ' || n, 1 + n % 10,
                       now() - make_interval(secs => n), now()
                from generate_series(1, 30000) n
                """.trimIndent(),
            ).param("author", java.util.UUID.fromString(authorId))
            .update()
        jdbc
            .sql(
                """
                insert into review_groups (review_id, group_id, category_id, shared_at)
                select id, :group, :category, created_at from reviews where author_id = :author
                """.trimIndent(),
            ).param("group", java.util.UUID.fromString(groupId))
            .param("category", java.util.UUID.fromString(categoryId))
            .param("author", java.util.UUID.fromString(authorId))
            .update()
        jdbc.sql("analyze review_groups").update()
        jdbc.sql("analyze reviews").update()

        val plan =
            jdbc
                .sql(
                    """
                    explain
                    select r.id from review_groups g join reviews r on r.id = g.review_id
                    where g.group_id = :group
                      and (g.shared_at, g.review_id) < (now(), 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)
                    order by g.shared_at desc, g.review_id desc limit 21
                    """.trimIndent(),
                ).param("group", java.util.UUID.fromString(groupId))
                .query(String::class.java)
                .list()
                .joinToString("\n")
        assertTrue("review_groups_feed_idx" in plan, plan)
        assertTrue("Seq Scan" !in plan, plan)

        val first = body(get("/groups/$groupId/reviews?limit=50", author))
        assertEquals(50, first["items"].size())
        assertTrue(first["items"][0]["title"].asString().startsWith("Рекомендация "))
    }
}
