//app/src/main/java/com/yahoosreach01/papa/SearchConditionEntity.kt
//ver 1.01-154
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
    val categories: String,           // 互換性維持用のカテゴリ文字列
    val excludeCategories: String,    // 除外カテゴリ文字列
    val targetService: String,        // "both", "auction", "fleamarket"
    val sortOrder: String             // "a", "s", "b", "e"
)
