//app/src/main/java/com/yahoosreach01/papa/AppDaos.kt
//ver 1.01-10
package com.yahoosreach01.papa

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE isExcluded = 0 ORDER BY createdAt DESC")
    fun getAllActiveItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isExcluded = 1 ORDER BY createdAt DESC")
    fun getExcludedItems(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: ItemEntity): Long

    @Query("UPDATE items SET isExcluded = 1 WHERE itemId = :itemId")
    suspend fun excludeItem(itemId: String)

    @Query("UPDATE items SET isNew = 0")
    suspend fun markAllAsRead()
}

@Dao
interface SearchConditionDao {
    @Query("SELECT * FROM search_conditions")
    fun getAllConditions(): Flow<List<SearchConditionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: SearchConditionEntity)
}

@Dao
interface GlobalExcludeKeyDao {
    @Query("SELECT * FROM global_exclude_keys")
    fun getAllKeys(): Flow<List<GlobalExcludeKeyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: GlobalExcludeKeyEntity)
    
    @Delete
    suspend fun deleteKey(key: GlobalExcludeKeyEntity)
}
