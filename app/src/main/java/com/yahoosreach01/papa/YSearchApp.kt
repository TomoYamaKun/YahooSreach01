//app/src/main/java/com/yahoosreach01/papa/YSearchApp.kt
//ver 1.00-01
package com.yahoosreach01.papa

import android.app.Application
import com.yahoosreach01.papa.utils.LogManager

class YSearchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // AndroidIDE等での開発時に起動直後のクラッシュを捕捉してログに記録する（要件16）
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogManager.e("CRASH", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        LogManager.d("App", "Application started. Version: ${Constants.APP_VERSION}")
    }
}
