//app/src/main/java/com/yahoosreach01/papa/utils/LogManager.kt
//ver 1.00-01
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

    fun getLogs(): String {
        return logs.joinToString("\n")
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AppLogs", getLogs())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "ログをクリップボードにコピーしました", Toast.LENGTH_SHORT).show()
    }
    
    fun clearLogs() {
        logs.clear()
    }
}
