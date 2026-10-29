//app/src/main/java/com/yahoosreach01/papa/ItemEntity.kt
//ver 1.02-04
package com.yahoosreach01.papa

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val itemId: String,
    val conditionId: Long,
    val title: String,
    val url: String,
    val imageUrl: String,
    val localImagePath: String?,
    val currentPrice: Int,
    val promptDecisionPrice: Int,
    val bidCount: Int,
    val shippingInfo: String?,
    val source: String,
    val description: String?,
    val isNew: Boolean,
    val isExcluded: Boolean,
    val isFavorite: Boolean = false, // お気に入りフラグ
    val endTime: String?,
    val createdAt: Long
)
