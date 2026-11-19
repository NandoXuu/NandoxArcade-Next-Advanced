package com.nandox.arcade.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "games", indices = [Index(value = ["uri"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platformId: String,
    val uri: String,
    val sourceId: Long = 0,
)

@Entity(tableName = "sources", indices = [Index(value = ["uri"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
)

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title")
    fun observeAll(): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGames(games: List<GameEntity>): List<Long>

    @Query("DELETE FROM games WHERE uri = :uri")
    suspend fun deleteGameByUri(uri: String)

    @Query("SELECT * FROM sources ORDER BY id")
    fun observeSources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun sources(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: SourceEntity): Long

    @Query("DELETE FROM games WHERE sourceId = :id")
    suspend fun deleteGamesBySource(id: Long)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSource(id: Long)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sources_uri` ON `sources` (`uri`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_uri` ON `games` (`uri`)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryKey` TEXT NOT NULL, `title` TEXT NOT NULL, `platformId` TEXT NOT NULL, `searchText` TEXT NOT NULL, `altTitles` TEXT NOT NULL, `developer` TEXT NOT NULL, `year` INTEGER NOT NULL, `genres` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_entryKey` ON `catalog` (`entryKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_platformId` ON `catalog` (`platformId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_searchText` ON `catalog` (`searchText`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_state` (`entryKey` TEXT NOT NULL, `acquired` INTEGER NOT NULL, PRIMARY KEY(`entryKey`))")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `games` ADD COLUMN `sourceId` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [GameEntity::class, SourceEntity::class, CatalogEntryEntity::class, CatalogStateEntity::class],
    version = 4,     exportSchema = true,
)
abstract class ArcadeDb : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun catalog(): CatalogDao

    companion object {
        @Volatile private var instance: ArcadeDb? = null

        fun get(context: Context): ArcadeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ArcadeDb::class.java, "arcade.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
