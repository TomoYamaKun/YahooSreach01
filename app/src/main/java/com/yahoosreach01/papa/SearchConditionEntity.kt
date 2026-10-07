//app/src/main/java/com/yahoosreach01/papa/SearchConditionEntity.kt
//ver 1.02-10
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
    val targetService: String
)
