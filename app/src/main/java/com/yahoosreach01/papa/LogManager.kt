//app/src/main/java/com/yahoosreach01/papa/utils/LogManager.kt
//ver 1.02-40
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
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    var isHtmlDebugEnabled: Boolean = false // メモリ圧迫を防ぐためOFF

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

    // ★ HTMLをメモリに溜め込まず、直接Downloadフォルダへ独立したファイルとして出力する
    fun exportHtmlDirectly(context: Context, modeLabel: String, pageNum: Int, htmlContent: String) {
        return
    }
   fun exportHtmlDirectly2(context: Context, modeLabel: String, pageNum: Int, htmlContent: String) {
        try {
            val timeString = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val safeLabel = modeLabel.replace(Regex("[^a-zA-Z0-9ー_]"), "_")
            val fileName = "YahooSreach01_HTML_${safeLabel}_P${pageNum}_$timeString.html"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(htmlContent.toByteArray(Charsets.UTF_8))
                    }
                    d("LogManager", "HTML直接ファイル出力成功: $fileName")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(htmlContent, Charsets.UTF_8)
                d("LogManager", "HTML直接ファイル出力成功: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            e("LogManager", "HTML直接ファイル出力失敗", e)
        }
    }

    fun getLogs(): String {
        return logs.joinToString("\n")
    }

    fun getHtmlLogs(): String {
        return "HTMLログは直接Downloadフォルダに出力されるため、メモリ表示を停止しています。"
    }

    fun copyToClipboard(context: Context, isHtml: Boolean = false) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = getLogs()
            val clip = ClipData.newPlainText("AppLogs", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "通常ログをコピーしました", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "コピーに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportLogsToFile2(context: Context) {
		return
	}
	fun exportLogsToFile(context: Context) {
        try {
            val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "YahooSreach01_Logs_$timeString.txt"
            val fullContent = StringBuilder().append("=== APP DEBUG LOGS ===\n").append(getLogs()).toString()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(fullContent.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "ログを出力しました:\n$fileName", Toast.LENGTH_LONG).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                file.writeText(fullContent, Charsets.UTF_8)
                Toast.makeText(context, "ログを出力しました:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "出力失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun clearLogs() {
        logs.clear()
    }
}
