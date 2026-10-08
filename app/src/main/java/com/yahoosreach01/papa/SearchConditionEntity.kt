//app/src/main/java/com/yahoosreach01/papa/SearchConditionEntity.kt
//ver 1.01-39
package com.yahoosreach01.papa

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_conditions")
data class SearchConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patternName: String,
    val searchKeys: String,
    val excludeKeys: String,
    val minPrice: Int,
    val maxPrice: Int,
    val categories: String,        // 対象カテゴリID（カンマ区切りなどで複数指定可能）
    val excludeCategories: String, // 除外カテゴリID
    val targetService: String,     // "both", "auction", "fleamarket"
    val sortOrder: String          // "a", "s", "b", "e"
)
