//app/src/main/java/com/yahoosreach01/papa/ItemEntity.kt
//ver 1.01-25
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
    val bidCount: Int, // 入札数
    val shippingInfo: String?, // 送料情報 (送料無料、着払いなど)
    val source: String, // "auction" or "fleamarket"
    val description: String?,
    val isNew: Boolean,
    val isExcluded: Boolean,
    val createdAt: Long
)
