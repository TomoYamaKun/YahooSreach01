//app/src/main/java/com/yahoosreach01/papa/AppDatabase.kt
//ver 1.01-22
package com.yahoosreach01.papa

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ItemEntity::class, SearchConditionEntity::class],
    version = 3, // バージョンを上げてスキーマ変更を確実に適用
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
                .fallbackToDestructiveMigration() // スキーマ変更時にクラッシュせず安全に再作成する
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
