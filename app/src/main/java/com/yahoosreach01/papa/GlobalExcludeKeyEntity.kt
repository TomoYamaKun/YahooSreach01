//app/src/main/java/com/yahoosreach01/papa/GlobalExcludeKeyEntity.kt
//ver 1.02-10
package com.yahoosreach01.papa

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_exclude_keys")
data class GlobalExcludeKeyEntity(
    @PrimaryKey val keyword: String,
    val isEnabled: Boolean = true
)
