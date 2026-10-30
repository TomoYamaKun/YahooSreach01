//app/src/main/java/com/yahoosreach01/papa/ConditionRepository.kt
//ver 1.02-34
package com.yahoosreach01.papa

import android.content.Context

class ConditionRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)

    suspend fun getAllConditions(): List<SearchConditionEntity> {
        return db.searchConditionDao().getAllConditionsList()
    }

    suspend fun insertCondition(condition: SearchConditionEntity) {
        db.searchConditionDao().insertCondition(condition)
    }

    suspend fun deleteCondition(condition: SearchConditionEntity) {
        db.searchConditionDao().deleteCondition(condition)
    }
}
