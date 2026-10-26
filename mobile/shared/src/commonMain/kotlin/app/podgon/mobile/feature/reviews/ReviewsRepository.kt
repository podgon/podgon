package app.podgon.mobile.feature.reviews

import app.podgon.contract.ReviewDetails
import app.podgon.contract.ReviewPage
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
    /** Страница ленты группы (`groupId == null` — мои рекомендации); в кэше — только первая страница. */
    suspend fun feed(
        groupId: String?,
        cursor: String?,
    ): Result<Cached<ReviewPage>> {
        val result = if (groupId == null) api.mine(cursor) else api.feed(groupId, cursor)
        val key = feedKey(groupId)
        return result.fold(
            onSuccess = { page ->
                if (cursor == null) cache.write(key, page)
                Result.success(Cached(page, fromCache = false))
            },
            onFailure = {
                val cached =
                    if (cursor == null &&
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

    suspend fun review(reviewId: String): Result<Cached<ReviewDetails>> {
        val result = api.get(reviewId)
        val key = reviewKey(reviewId)
        return result.fold(
            onSuccess = {
                cache.write(key, it)
                Result.success(Cached(it, fromCache = false))
            },
            onFailure = {
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

    private fun feedKey(groupId: String?) = if (groupId == null) "mine" else "feed:$groupId"

    private fun reviewKey(reviewId: String) = "review:$reviewId"
}
