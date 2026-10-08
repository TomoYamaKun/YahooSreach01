//app/src/main/java/com/yahoosreach01/papa/AppDatabase.kt
//ver 1.01-14
package com.yahoosreach01.papa

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ItemEntity::class, SearchConditionEntity::class],
    version = 2, // バージョンを2に上げて古いグローバル除外キーテーブルをクリア
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun searchConditionDao(): SearchConditionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ysearch_database"
                )
                .fallbackToDestructiveMigration() // DB構造変更時の自動リセット
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
