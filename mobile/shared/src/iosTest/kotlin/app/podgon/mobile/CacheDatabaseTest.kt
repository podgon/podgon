package app.podgon.mobile

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.podgon.contract.ReviewPage
import app.podgon.mobile.core.cache.CacheDatabase
import app.podgon.mobile.core.cache.ResponseCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Настоящий Room на SQLite (iOS-симулятор): запись, чтение, замена, повреждённая запись, очистка. */
class CacheDatabaseTest {
    private fun cache() =
        ResponseCache(
            Room
                .inMemoryDatabaseBuilder<CacheDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
                .responses(),
        )

    @Test
    fun `страница записывается читается и заменяется`() =
        runTest {
            val cache = cache()
            assertNull(cache.read<ReviewPage>("feed:g1"))
            cache.write("feed:g1", ReviewPage(items = emptyList(), nextCursor = "c1"))
            assertEquals("c1", cache.read<ReviewPage>("feed:g1")?.nextCursor)
            cache.write("feed:g1", ReviewPage(items = emptyList(), nextCursor = null))
            assertNull(cache.read<ReviewPage>("feed:g1")?.nextCursor)
        }

    @Test
    fun `повреждённая запись читается как отсутствующая а очистка удаляет всё`() =
        runTest {
            val cache = cache()
            cache.write("feed:g1", "не страница")
            assertNull(cache.read<ReviewPage>("feed:g1"))
            cache.write("mine", ReviewPage(items = emptyList()))
            cache.clear()
            assertNull(cache.read<ReviewPage>("mine"))
        }
}
