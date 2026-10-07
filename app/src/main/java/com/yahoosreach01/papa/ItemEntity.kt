//app/src/main/java/com/yahoosreach01/papa/ItemEntity.kt
//ver 1.02-10
package com.yahoosreach01.papa

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val itemId: String,
    val title: String,
    val url: String,
    val imageUrl: String,
    val localImagePath: String?,
    val currentPrice: Int,
    val promptDecisionPrice: Int,
    val source: String,
    val isNew: Boolean,
    val isExcluded: Boolean,
    val createdAt: Long
)
