//app/src/main/java/com/yahoosreach01/papa/utils/LogManager.kt
//ver 1.01-130
package com.yahoosreach01.papa.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val logs = mutableListOf<String>()
    private val htmlLogs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // HTMLデバッグのON/OFFフラグ（デフォルトは負荷軽減のためOFF、必要に応じてUIから切り替え可能）
    var isHtmlDebugEnabled: Boolean = false

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
        if (!isHtmlDebugEnabled) return // OFFのときは蓄積しない
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
    
    fun clearLogs() {
        logs.clear()
        htmlLogs.clear()
    }
}
