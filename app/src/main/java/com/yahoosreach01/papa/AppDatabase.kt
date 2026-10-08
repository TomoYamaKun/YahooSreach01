//app/src/main/java/com/yahoosreach01/papa/AppDatabase.kt
//ver 1.01-32
package com.yahoosreach01.papa

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ItemEntity::class, SearchConditionEntity::class],
    version = 5, // バージョンを5に上げてスキーマ変更を完全に反映
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
                .fallbackToDestructiveMigration() // スキーマ変更時に安全に再作成
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
