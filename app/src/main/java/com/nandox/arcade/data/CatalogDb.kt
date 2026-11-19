package com.nandox.arcade.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "catalog",
    indices = [
        Index(value = ["entryKey"], unique = true),
        Index(value = ["platformId"]),
        Index(value = ["searchText"]),
    ],
)
data class CatalogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryKey: String,
    val title: String,
    val platformId: String,
    val searchText: String,
    val altTitles: String,
    val developer: String,
    val year: Int,
    val genres: String,
)

@Entity(tableName = "catalog_state")
data class CatalogStateEntity(
    @PrimaryKey val entryKey: String,
    val acquired: Boolean,
)

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CatalogEntryEntity>)

    @Query(
        "SELECT * FROM catalog WHERE (:platform IS NULL OR platformId = :platform) " +
            "AND searchText LIKE :pattern ORDER BY title LIMIT :limit OFFSET :offset"
    )
    suspend fun search(platform: String?, pattern: String, limit: Int, offset: Int): List<CatalogEntryEntity>

    @Query("SELECT * FROM catalog_state")
    fun observeStates(): Flow<List<CatalogStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: CatalogStateEntity)
}
