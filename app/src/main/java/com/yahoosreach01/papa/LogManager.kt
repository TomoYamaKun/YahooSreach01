//app/src/main/java/com/yahoosreach01/papa/utils/LogManager.kt
//ver 1.01-02
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
        htmlLogs.add(message)
    }

    fun getLogs(): String {
        return logs.joinToString("\n")
    }

    fun getHtmlLogs(): String {
        return htmlLogs.joinToString("\n")
    }

    fun copyToClipboard(context: Context, isHtml: Boolean = false) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (isHtml) getHtmlLogs() else getLogs()
        val clip = ClipData.newPlainText(if (isHtml) "AppHtmlLogs" else "AppLogs", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, if (isHtml) "HTMLログをコピーしました" else "通常ログをコピーしました", Toast.LENGTH_SHORT).show()
    }
    
    fun clearLogs() {
        logs.clear()
        htmlLogs.clear()
    }
}
