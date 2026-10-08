//app/src/main/java/com/yahoosreach01/papa/SearchConditionEntity.kt
//ver 1.01-30
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
    val categories: String,
    val targetService: String,
    val sortOrder: String = "a" // "a"=おすすめ/新着, "s"=価格安い順, "b"=入札件数順, "e"=残り時間
)
