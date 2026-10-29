//app/src/main/java/com/yahoosreach01/papa/utils/LogManager.kt
//ver 1.02-27
package com.yahoosreach01.papa.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val logs = mutableListOf<String>()
    private val htmlLogs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    var isHtmlDebugEnabled: Boolean = true

    fun d(tag: String, message: String) {
        val logStr = "${dateFormat.format(Date())} [DEBUG] $tag: $message"
        logs.add(logStr)
        android.util.Log.d(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val logStr = "${dateFormat.format(Date())} [ERROR] $tag: $message ${throwable?.message ?: ""}"
        logs.add(logStr)
        android.util.Log.e(tag, message, throwable)
    }

    fun html(message: String) {
        if (!isHtmlDebugEnabled) return
        val timestampedHtml = "${dateFormat.format(Date())}\n$message"
        htmlLogs.add(timestampedHtml)
    }

    fun getLogs(): String {
        return logs.joinToString("\n")
    }

    fun getHtmlLogs(): String {
        if (!isHtmlDebugEnabled) return "HTMLデバッグは現在 OFF です。"
        return htmlLogs.joinToString("\n\n========================================\n\n")
    }

    fun copyToClipboard(context: Context, isHtml: Boolean = false) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = if (isHtml) getHtmlLogs() else getLogs()

            val clip = ClipData.newPlainText(if (isHtml) "AppHtmlLogs" else "AppLogs", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, if (isHtml) "HTMLログをコピーしました" else "通常ログをコピーしました", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e(LogManager::class.java.simpleName, "クリップボードコピー失敗", e)
            Toast.makeText(context, "コピーに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    // ★ 共有ストレージの Download フォルダ直下へログファイルを出力する機能
    fun exportLogsToFile(context: Context) {
        try {
            val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "YahooSreach01_Logs_$timeString.txt"

            val fullContent = StringBuilder()
            fullContent.append("=== APP DEBUG LOGS ===\n")
            fullContent.append(getLogs())
            fullContent.append("\n\n========================================\n\n")
            fullContent.append("=== HTML DEBUG LOGS ===\n")
            fullContent.append(getHtmlLogs())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10以降 (MediaStore経由で Download 直下に保存)
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(fullContent.toString().toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Downloadフォルダに出力しました:\n$fileName", Toast.LENGTH_LONG).show()
                    d("LogManager", "ログファイル出力完了 (MediaStore): $fileName")
                } else {
                    throw Exception("MediaStoreのURI取得に失敗しました")
                }
            } else {
                // Android 9以前 (従来のファイル直接書き込み)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(fullContent.toString(), Charsets.UTF_8)
                Toast.makeText(context, "Downloadフォルダに出力しました:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
                d("LogManager", "ログファイル出力完了: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            e("LogManager", "ログファイル出力失敗", e)
            Toast.makeText(context, "出力失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun clearLogs() {
        logs.clear()
        htmlLogs.clear()
    }
}
