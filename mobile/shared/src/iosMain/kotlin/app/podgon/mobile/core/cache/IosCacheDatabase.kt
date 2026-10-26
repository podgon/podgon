package app.podgon.mobile.core.cache

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** БД кэша в каталоге Caches (система может очистить его сама — для кэша это нормально). */
@OptIn(ExperimentalForeignApi::class)
fun buildCacheDatabase(): CacheDatabase {
    val directory =
        NSFileManager.defaultManager
            .URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)
            ?.path
            ?: error("нет каталога Caches")
    return Room
        .databaseBuilder<CacheDatabase>("$directory/cache.db")
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
