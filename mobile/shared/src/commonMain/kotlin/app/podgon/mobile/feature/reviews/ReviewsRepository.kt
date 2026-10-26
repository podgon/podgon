package app.podgon.mobile.feature.reviews

import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.TagCount
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.ReviewsApi

/** Результат чтения: само значение и признак, что оно взято из локального кэша (нет сети). */
data class Cached<T>(
    val value: T,
    val fromCache: Boolean,
)

/**
 * Чтение рекомендаций с запасным вариантом из кэша: первая страница каждой ленты и открытые карточки
 * сохраняются, при отсутствии сети показываются они. Правки только онлайн — запись идёт напрямую через API.
 */
class ReviewsRepository(
    private val api: ReviewsApi,
    private val cache: ResponseCache,
) {
    /**
     * Страница ленты ([scope]) с фильтром. В кэш попадает только первая страница без фильтра; при сетевой
     * ошибке с фильтром или на следующей странице сохранённое не подставляется.
     */
    suspend fun feed(
        scope: FeedScope,
        filter: ReviewFilter,
        cursor: String?,
    ): Result<Cached<ReviewPage>> {
        val result =
            when (scope) {
                is FeedScope.Group -> api.feed(scope.groupId, filter, cursor)
                FeedScope.Mine -> api.mine(cursor)
                FeedScope.AllGroups -> api.search(filter, cursor)
            }
        val key = feedKey(scope)
        val cacheable = cursor == null && filter.isEmpty
        return result.fold(
            onSuccess = { page ->
                if (cacheable) cache.write(key, page)
                Result.success(Cached(page, fromCache = false))
            },
            onFailure = {
                if (scope is FeedScope.Group && result.lostAccess()) forgetGroup(scope.groupId)
                val cached =
                    if (cacheable &&
                        result.appError() == AppError.Network
                    ) {
                        cache.read<ReviewPage>(key)
                    } else {
                        null
                    }
                if (cached != null) Result.success(Cached(cached, fromCache = true)) else Result.failure(it)
            },
        )
    }

    suspend fun tags(groupId: String): Result<List<TagCount>> = api.tags(groupId)

    /** Последний фильтр ленты группы (хранится в локальном кэше и очищается при выходе). */
    suspend fun savedFilter(groupId: String): ReviewFilter? = cache.read<ReviewFilter>(filterKey(groupId))

    suspend fun saveFilter(
        groupId: String,
        filter: ReviewFilter,
    ) {
        if (filter.isEmpty) cache.remove(filterKey(groupId)) else cache.write(filterKey(groupId), filter)
    }

    suspend fun review(reviewId: String): Result<Cached<ReviewDetails>> {
        val result = api.get(reviewId)
        val key = reviewKey(reviewId)
        return result.fold(
            onSuccess = {
                cache.write(key, it)
                Result.success(Cached(it, fromCache = false))
            },
            onFailure = {
                if (result.lostAccess()) cache.remove(key)
                val cached = if (result.appError() == AppError.Network) cache.read<ReviewDetails>(key) else null
                if (cached != null) Result.success(Cached(cached, fromCache = true)) else Result.failure(it)
            },
        )
    }

    suspend fun delete(reviewId: String): Result<Unit> {
        val result = api.delete(reviewId)
        if (result.isSuccess) cache.remove(reviewKey(reviewId))
        return result
    }

    /**
     * Убирает из кэша ленты и фильтры групп, в которых пользователь больше не состоит (замечание R14 ревью):
     * после исключения из группы её данные не должны показываться без сети. Вызывается после загрузки списка групп.
     */
    suspend fun retainGroups(groupIds: Set<String>) {
        listOf(FEED_PREFIX, FILTER_PREFIX).forEach { prefix ->
            cache.keys(prefix).filter { it.removePrefix(prefix) !in groupIds }.forEach { cache.remove(it) }
        }
    }

    private suspend fun forgetGroup(groupId: String) {
        cache.remove(FEED_PREFIX + groupId)
        cache.remove(filterKey(groupId))
    }

    /** Сервер ответил «нет доступа» (не участник → 404, нет прав → 403): сохранённое больше не показываем. */
    private fun Result<*>.lostAccess(): Boolean = (appError() as? AppError.Api)?.status in setOf(403, 404)

    private fun feedKey(scope: FeedScope) =
        when (scope) {
            is FeedScope.Group -> FEED_PREFIX + scope.groupId
            FeedScope.Mine -> "mine"
            FeedScope.AllGroups -> "all"
        }

    private fun filterKey(groupId: String) = FILTER_PREFIX + groupId

    private fun reviewKey(reviewId: String) = "review:$reviewId"
}

private const val FEED_PREFIX = "feed:"
private const val FILTER_PREFIX = "filter:"
