package app.podgon.mobile.feature.reviews

import app.podgon.contract.FeedToc
import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
import app.podgon.contract.TagCount
import app.podgon.mobile.core.cache.ResponseCache
import app.podgon.mobile.core.error.AppError
import app.podgon.mobile.core.error.appError
import app.podgon.mobile.core.network.ReviewsApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Рекомендация [reviewId] добавлена в «Избранное» или убрана из него. */
data class FavoriteChange(
    val reviewId: String,
    val favorite: Boolean,
)

/** Результат чтения: само значение и признак, что оно взято из локального кэша (нет сети). */
data class Cached<T>(
    val value: T,
    val fromCache: Boolean,
)

/**
 * Чтение рекомендаций с запасным вариантом из кэша: первая страница каждой ленты (своя для каждого сочетания
 * области, фильтра и секции), оглавления и открытые карточки сохраняются, при отсутствии сети показываются они.
 * Правки только онлайн — запись идёт напрямую через API.
 */
class ReviewsRepository(
    private val api: ReviewsApi,
    private val cache: ResponseCache,
) {
    /**
     * Страница ленты ([scope]) с фильтром и (для секции оглавления) [section]. В кэш попадает первая страница
     * под ключом «область + секция + фильтр»; следующие страницы при сетевой ошибке из кэша не берутся.
     */
    suspend fun feed(
        scope: FeedScope,
        filter: ReviewFilter,
        cursor: String?,
        section: String? = null,
    ): Result<Cached<ReviewPage>> {
        val result =
            when (scope) {
                is FeedScope.Group -> api.feed(scope.groupId, filter, cursor, section)
                FeedScope.Mine -> api.mine(cursor)
                FeedScope.AllGroups -> api.search(filter, cursor, section)
            }
        val key = variantKey(feedKey(scope), filter, section)
        val cacheable = cursor == null
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

    /** Оглавление ленты группы или «Все» ([groupId] `null`); без сети — сохранённое для того же фильтра. */
    suspend fun toc(
        groupId: String?,
        filter: ReviewFilter,
    ): Result<Cached<FeedToc>> {
        val result = api.toc(groupId, filter)
        val key = variantKey(TOC_PREFIX + (groupId ?: "all"), filter.copy(query = ""), null)
        return result.fold(
            onSuccess = {
                cache.write(key, it)
                Result.success(Cached(it, fromCache = false))
            },
            onFailure = {
                if (groupId != null && result.lostAccess()) forgetGroup(groupId)
                val cached = if (result.appError() == AppError.Network) cache.read<FeedToc>(key) else null
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

    private val _favoriteChanges = MutableSharedFlow<FavoriteChange>(extraBufferCapacity = 16)

    /** Изменения «Избранного» с любого экрана: открытые ленты обновляют сердечко у себя. */
    val favoriteChanges: SharedFlow<FavoriteChange> = _favoriteChanges.asSharedFlow()

    /** Добавить в «Избранное» или убрать (только онлайн); сохранённая карточка устаревает. */
    suspend fun setFavorite(
        reviewId: String,
        favorite: Boolean,
    ): Result<Unit> {
        val result = api.setFavorite(reviewId, favorite)
        if (result.isSuccess) {
            cache.remove(reviewKey(reviewId))
            _favoriteChanges.emit(FavoriteChange(reviewId, favorite))
        }
        return result
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
        listOf(FEED_PREFIX, TOC_PREFIX, FILTER_PREFIX).forEach { prefix ->
            cache
                .keys(prefix)
                .filter { key ->
                    key.removePrefix(prefix).substringBefore(VARIANT).let { it !in groupIds && it != "all" }
                }.forEach { cache.remove(it) }
        }
    }

    private suspend fun forgetGroup(groupId: String) {
        listOf(FEED_PREFIX, TOC_PREFIX).forEach { prefix ->
            cache
                .keys(prefix + groupId)
                .filter { it.removePrefix(prefix + groupId).let { rest -> rest.isEmpty() || rest.startsWith(VARIANT) } }
                .forEach { cache.remove(it) }
        }
        cache.remove(filterKey(groupId))
    }

    /** Ключ варианта ленты: без фильтра и секции — сам [base] (как до этапа 8.2.2), иначе с их описанием. */
    private fun variantKey(
        base: String,
        filter: ReviewFilter,
        section: String?,
    ): String =
        if (filter.isEmpty && section == null) {
            base
        } else {
            base + VARIANT + section.orEmpty() + VARIANT + cache.json.encodeToString(ReviewFilter.serializer(), filter)
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
private const val TOC_PREFIX = "toc:"
private const val VARIANT = "|"
private const val FILTER_PREFIX = "filter:"
