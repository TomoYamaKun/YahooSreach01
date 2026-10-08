//app/src/main/java/com/yahoosreach01/papa/AppDaos.kt
//ver 1.01-29
package com.yahoosreach01.papa

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE isExcluded = 0 ORDER BY createdAt DESC")
    fun getAllActiveItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isExcluded = 1 ORDER BY createdAt DESC")
    fun getAllExcludedItems(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    // 検索実行時に古い未除外アイテムをクリアして画面をリフレッシュする
    @Query("DELETE FROM items WHERE conditionId = :conditionId AND isExcluded = 0")
    suspend fun clearActiveItemsForCondition(conditionId: Long)

    @Query("UPDATE items SET isExcluded = 1 WHERE itemId = :itemId")
    suspend fun excludeItem(itemId: String)

    @Query("UPDATE items SET isExcluded = 0 WHERE itemId = :itemId")
    suspend fun restoreItem(itemId: String)

    @Query("UPDATE items SET isNew = 0")
    suspend fun markAllAsRead()
}

@Dao
interface SearchConditionDao {
    @Query("SELECT * FROM search_conditions")
    fun getAllConditions(): Flow<List<SearchConditionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: SearchConditionEntity)

    @Update
    suspend fun updateCondition(condition: SearchConditionEntity)

    @Delete
    suspend fun deleteCondition(condition: SearchConditionEntity)
}
