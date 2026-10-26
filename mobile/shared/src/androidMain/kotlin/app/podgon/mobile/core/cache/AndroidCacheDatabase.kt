package app.podgon.mobile.core.cache

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** БД кэша в каталоге баз приложения; при смене схемы кэш просто пересоздаётся (данные восстановимы с сервера). */
fun buildCacheDatabase(context: Context): CacheDatabase =
    Room
        .databaseBuilder<CacheDatabase>(context, context.getDatabasePath("cache.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
