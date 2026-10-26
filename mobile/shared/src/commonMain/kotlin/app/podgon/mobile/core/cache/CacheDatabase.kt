package app.podgon.mobile.core.cache

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Upsert

/** Сохранённый ответ сервера: ключ (`feed:<groupId>`, `mine`, `review:<id>`) и его JSON. */
@Entity(tableName = "cached_response")
data class CachedResponse(
    @PrimaryKey val key: String,
    val json: String,
    /** Момент сохранения, мс с начала эпохи. */
    val savedAt: Long,
)

@Dao
interface CachedResponseDao {
    @Query("select * from cached_response where `key` = :key")
    suspend fun find(key: String): CachedResponse?

    @Upsert
    suspend fun save(entry: CachedResponse)

    @Query("delete from cached_response where `key` = :key")
    suspend fun delete(key: String)

    @Query("delete from cached_response")
    suspend fun clear()

    @Query("select `key` from cached_response where `key` like :prefix || '%'")
    suspend fun keys(prefix: String): List<String>
}

/** Локальный кэш для чтения без сети: первые страницы лент и открытые карточки. */
@Database(entities = [CachedResponse::class], version = 1, exportSchema = true)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun responses(): CachedResponseDao
}

// Реализацию (actual) генерирует Room для каждой платформы
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}
