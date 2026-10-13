//app/src/main/java/com/yahoosreach01/papa/CategoryEntity.kt
//ver 1.01-157
package com.yahoosreach01.papa

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val categoryName: String,
    val categoryValue: String
)
