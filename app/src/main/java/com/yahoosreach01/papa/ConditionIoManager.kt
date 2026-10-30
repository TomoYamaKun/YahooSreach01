//app/src/main/java/com/yahoosreach01/papa/utils/ConditionIoManager.kt
//ver 1.02-34
package com.yahoosreach01.papa.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.yahoosreach01.papa.AppDatabase
import com.yahoosreach01.papa.SearchConditionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConditionIoManager {

    suspend fun exportConditions(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            val conditions = db.searchConditionDao().getAllConditionsList()

            val jsonArray = JSONArray()
            for (cond in conditions) {
                val obj = JSONObject().apply {
                    put("patternName", cond.patternName)
                    put("searchKeys", cond.searchKeys)
                    put("excludeKeys", cond.excludeKeys)
                    put("minPrice", cond.minPrice)
                    put("maxPrice", cond.maxPrice)
                    put("categories", cond.categories)
                    put("excludeCategories", cond.excludeCategories)
                    put("targetService", cond.targetService)
                    put("sortOrder", cond.sortOrder)
                }
                jsonArray.put(obj)
            }

            val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "YahooSreach01_Conditions_$timeString.json"
            val fileContent = jsonArray.toString(4)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(fileContent.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "検索条件をDownloadに出力しました:\n$fileName", Toast.LENGTH_LONG).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                file.writeText(fileContent, Charsets.UTF_8)
                Toast.makeText(context, "検索条件を出力しました:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
            LogManager.d("ConditionIoManager", "検索条件エクスポート成功: ${conditions.size}件")
        } catch (e: Exception) {
            LogManager.e("ConditionIoManager", "検索条件エクスポート失敗", e)
            Toast.makeText(context, "エクスポート失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun importConditionsFromFile(context: Context, fileName: String): Int {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            if (!file.exists()) {
                throw Exception("ファイルが見つかりません: $fileName")
            }
            val jsonString = file.readText(Charsets.UTF_8)

            val jsonArray = JSONArray(jsonString)
            val db = AppDatabase.getDatabase(context)
            var importCount = 0

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val entity = SearchConditionEntity(
                    patternName = obj.optString("patternName", "インポート条件"),
                    searchKeys = obj.optString("searchKeys", ""),
                    excludeKeys = obj.optString("excludeKeys", ""),
                    minPrice = obj.optInt("minPrice", 0),
                    maxPrice = obj.optInt("maxPrice", 0),
                    categories = obj.optString("categories", ""),
                    excludeCategories = obj.optString("excludeCategories", ""),
                    targetService = obj.optString("targetService", "auction"),
                    sortOrder = obj.optString("sortOrder", "s")
                )
                db.searchConditionDao().insertCondition(entity)
                importCount++
            }

            LogManager.d("ConditionIoManager", "検索条件インポート成功: ${importCount}件")
            Toast.makeText(context, "${importCount}件の検索条件をインポートしました", Toast.LENGTH_LONG).show()
            return importCount
        } catch (e: Exception) {
            LogManager.e("ConditionIoManager", "検索条件インポート失敗", e)
            Toast.makeText(context, "インポート失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            return 0
        }
    }
}
