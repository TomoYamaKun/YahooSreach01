//app/src/main/java/com/yahoosreach01/papa/AppDaos.kt
//ver 1.02-05
package com.yahoosreach01.papa

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE conditionId = :conditionId AND isExcluded = 0 ORDER BY isFavorite DESC")
    fun getActiveItemsForCondition(conditionId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE conditionId = :conditionId AND isExcluded = 1 ORDER BY createdAt DESC")
    fun getExcludedItemsForCondition(conditionId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE conditionId = :conditionId AND isExcluded = 1")
    suspend fun getExcludedItemsListForCondition(conditionId: Long): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Query("DELETE FROM items WHERE conditionId = :conditionId AND isExcluded = 0")
    suspend fun clearActiveItemsForCondition(conditionId: Long)

    @Query("UPDATE items SET isExcluded = 1 WHERE itemId = :itemId")
    suspend fun excludeItem(itemId: String)

    @Query("UPDATE items SET isExcluded = 0 WHERE itemId = :itemId")
    suspend fun restoreItem(itemId: String)

    @Query("UPDATE items SET isNew = 0")
    suspend fun markAllAsRead()

    @Query("UPDATE items SET isFavorite = :isFavorite WHERE itemId = :itemId")
    suspend fun setFavorite(itemId: String, isFavorite: Boolean)

    @Query("SELECT * FROM items WHERE conditionId = :conditionId")
    suspend fun getItemsListForCondition(conditionId: Long): List<ItemEntity>

    @Query("SELECT * FROM items")
    suspend fun getAllItemsList(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<ItemEntity>)
}

@Dao
interface SearchConditionDao {
    @Query("SELECT * FROM search_conditions")
    fun getAllConditions(): Flow<List<SearchConditionEntity>>

    @Query("SELECT * FROM search_conditions")
    suspend fun getAllConditionsList(): List<SearchConditionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: SearchConditionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllConditions(conditions: List<SearchConditionEntity>)

    @Update
    suspend fun updateCondition(condition: SearchConditionEntity)

    @Delete
    suspend fun deleteCondition(condition: SearchConditionEntity)

    @Query("DELETE FROM search_conditions")
    suspend fun deleteAllConditions()
}
