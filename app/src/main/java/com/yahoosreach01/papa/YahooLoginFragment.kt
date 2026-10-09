//app/src/main/java/com/yahoosreach01/papa/YahooLoginFragment.kt
//ver 1.01-95
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

    private var webView: WebView? = null

    companion object {
        // 共有の保留用URL（フラグメント生成前や非アクティブ時でも確実にURLを渡せるようにする）
        private var targetUrlToLoad: String? = null

        fun requestLoadUrl(url: String) {
            targetUrlToLoad = url
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (webView == null) {
            webView = WebView(requireContext()).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let { view?.loadUrl(it) }
                        return true
                    }

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
                
                // 外部から指定されたURLがあればそれを優先し、なければ初期起動時としてログインページを表示
                val initialUrl = targetUrlToLoad ?: "https://login.yahoo.co.jp/config/login?.src=auc"
                targetUrlToLoad = null
                loadUrl(initialUrl)
            }
        } else {
            // すでにWebViewがある状態で保留URLがあればロード
            targetUrlToLoad?.let {
                targetUrlToLoad = null
                webView?.loadUrl(it)
            }
        }
        return webView!!
    }

    // 外部（検索結果リスト等）から商品がタップされたときに、このタブ内でURLを開くためのメソッド
    fun loadItemUrl(url: String) {
        val currentWebView = webView
        if (currentWebView != null) {
            currentWebView.loadUrl(url)
        } else {
            targetUrlToLoad = url
        }
    }
}
