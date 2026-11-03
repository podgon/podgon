package app.podgon.reviews.domain

import app.podgon.contract.AuthorCount
import app.podgon.contract.CategoryCount
import app.podgon.contract.Facets
import app.podgon.contract.FeedSection
import app.podgon.contract.FeedSectionKind
import app.podgon.contract.FeedToc
import app.podgon.contract.ReviewAuthor
import app.podgon.contract.SECTION_CATEGORY_PREFIX
import app.podgon.contract.SECTION_FILTER_PREFIX
import app.podgon.contract.TagCount
import app.podgon.identity.UserDirectory
import app.podgon.reviews.persistence.FeedRepository
import app.podgon.reviews.persistence.SavedFilterRepository
import app.podgon.reviews.persistence.SearchFilters
import app.podgon.reviews.persistence.SectionStats
import org.springframework.stereotype.Service
import java.util.UUID

private const val FACET_TAGS_LIMIT = 100

/**
 * Оглавление ленты (И29) и счётчики для панели фильтров. Область и фильтры — как у ленты ([FeedFilters]);
 * секции и счётчики считаются запросом, денормализованно не хранятся.
 */
@Service
class FeedService(
    private val filters: FeedFilters,
    private val feed: FeedRepository,
    private val savedFilters: SavedFilterRepository,
    private val categories: CategoryService,
    private val users: UserDirectory,
) {
    /**
     * Секции в порядке показа: закреплённые фильтры → закреплённые категории (в порядке пользователя) →
     * остальные корневые категории по самой свежей публикации, «Без категории» — последней. Пустых нет.
     * [groupId] `null` — «Все»; фильтр панели без поиска накладывается на каждую секцию.
     */
    fun toc(
        userId: UUID,
        groupId: UUID?,
        params: SearchParams,
    ): FeedToc {
        val panel = filters.parse(userId, params.copy(q = null, section = null, cursor = null))
        val scope = filters.scope(userId, groupId?.let(::listOf))
        if (scope.isEmpty()) return FeedToc(emptyList())
        val pinned = savedFilters.list(userId).filter { it.inFeed }.sortedBy { it.position }
        val roots = feed.rootSections(scope, panel)
        val filterSections =
            pinned.filter { it.kind == FILTER_KIND }.mapNotNull { f ->
                val criteria = filters.sectionCriteria(filters.decode(f.definition!!))
                feed.stats(scope, panel.copy(criteria = panel.criteria + criteria))?.let {
                    FeedSection(
                        section = SECTION_FILTER_PREFIX + f.id,
                        kind = FeedSectionKind.FILTER,
                        pinned = true,
                        count = it.count,
                        lastSharedAt = it.lastSharedAt.toString(),
                        filterId = f.id.toString(),
                        name = f.name,
                    )
                }
            }
        val pinnedCategories = pinned.filter { it.kind == CATEGORY_KIND }
        val categorySections =
            pinnedCategories.mapNotNull { c ->
                roots[c.categoryId]?.let { categorySection(c.categoryId!!, it, c.id) }
            }
        val pinnedIds = pinnedCategories.mapNotNull { it.categoryId }.toSet()
        val others =
            roots
                .filterKeys { it !in pinnedIds }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<UUID, SectionStats>> { categories.isSystem(it.key) }
                        .thenByDescending { it.value.lastSharedAt }
                        .thenBy { it.key },
                ).map { categorySection(it.key, it.value, null) }
        return FeedToc(filterSections + categorySections + others)
    }

    /** Счётчики выборки (те же параметры, что у ленты, без курсора); в «Личном» авторов не показываем. */
    fun facets(
        userId: UUID,
        groupIds: List<UUID>?,
        params: SearchParams,
    ): Facets {
        val parsed = filters.parse(userId, params.copy(cursor = null))
        val scope = filters.scope(userId, groupIds)
        if (scope.isEmpty()) return Facets(emptyList(), emptyList(), emptyList())
        return Facets(
            categories = categoryCounts(scope, parsed),
            tags = feed.tagCounts(scope, parsed, FACET_TAGS_LIMIT).map { TagCount(it.name, it.count) },
            authors = if (scope == setOf(filters.system(userId).personal)) emptyList() else authors(scope, parsed),
        )
    }

    /** Корни и подкатегории выборки; у корня — вместе с подкатегориями. */
    private fun categoryCounts(
        scope: Set<UUID>,
        parsed: SearchFilters,
    ): List<CategoryCount> {
        val totals = mutableMapOf<UUID, Int>()
        feed.categoryCounts(scope, parsed).forEach { (id, count) ->
            totals.merge(id, count, Int::plus)
            categories.rootOf(id).takeIf { it != id }?.let { totals.merge(it, count, Int::plus) }
        }
        return totals.entries
            .sortedWith(compareByDescending<Map.Entry<UUID, Int>> { it.value }.thenBy { it.key })
            .map { CategoryCount(it.key.toString(), it.value) }
    }

    private fun authors(
        scope: Set<UUID>,
        parsed: SearchFilters,
    ): List<AuthorCount> {
        val counts = feed.authorCounts(scope, parsed)
        val known = users.findAll(counts.map { it.first })
        return counts.mapNotNull { (id, count) ->
            known[id]?.let { AuthorCount(ReviewAuthor(it.id.toString(), it.username, it.displayName), count) }
        }
    }

    private fun categorySection(
        categoryId: UUID,
        stats: SectionStats,
        filterId: UUID?,
    ) = FeedSection(
        section = SECTION_CATEGORY_PREFIX + categoryId,
        kind = FeedSectionKind.CATEGORY,
        pinned = filterId != null,
        count = stats.count,
        lastSharedAt = stats.lastSharedAt.toString(),
        categoryId = categoryId.toString(),
        filterId = filterId?.toString(),
    )
}
