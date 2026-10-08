//app/src/main/java/com/yahoosreach01/papa/YahooLoginFragment.kt
//ver 1.01-68
package com.yahoosreach01.papa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.yahoosreach01.papa.utils.LogManager

class YahooLoginFragment : Fragment() {

    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        LogManager.d("YahooLoginFragment", "現在のURL: $url")
                        if (url.contains("yahoo.co.jp") && !url.contains("login")) {
                            val cookieManager = CookieManager.getInstance()
                            val cookies = cookieManager.getCookie(url)
                            LogManager.d("YahooLoginFragment", "取得したCookie: $cookies")
                        }
                    }
                }
            }
            loadUrl("https://login.yahoo.co.jp/config/login?.src=auc")
        }
        return webView
    }
}
