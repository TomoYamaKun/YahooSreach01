//app/src/main/java/com/yahoosreach01/papa/YahooLoginFragment.kt
//ver 1.02-00
package com.yahoosreach01.papa

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.yahoosreach01.papa.databinding.FragmentYahooLoginBinding
import com.yahoosreach01.papa.utils.LogManager

class YahooLoginFragment : Fragment() {

    private var _binding: FragmentYahooLoginBinding? = null
    private val binding get() = _binding!!

    companion object {
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
        _binding = FragmentYahooLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.webView.apply {
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
            
            val initialUrl = targetUrlToLoad ?: "https://login.yahoo.co.jp/config/login?.src=auc"
            targetUrlToLoad = null
            loadUrl(initialUrl)
        }

        // 戻るボタン
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                Toast.makeText(requireContext(), "これ以上戻れません", Toast.LENGTH_SHORT).show()
            }
        }

        // 上部バーのダブルタップでYahooトップ画面に戻る
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                binding.webView.loadUrl("https://www.yahoo.co.jp")
                Toast.makeText(requireContext(), "Yahooトップに戻りました", Toast.LENGTH_SHORT).show()
                return true
            }
        })
        binding.layoutTopBar.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    fun loadItemUrl(url: String) {
        val wv = _binding?.webView
        if (wv != null) {
            wv.loadUrl(url)
        } else {
            targetUrlToLoad = url
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
