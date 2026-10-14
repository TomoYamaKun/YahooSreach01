//app/src/main/java/com/yahoosreach01/papa/CategoryBrowserFragment.kt
//ver 1.02-00
package com.yahoosreach01.papa

import android.app.AlertDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yahoosreach01.papa.databinding.FragmentCategoryBrowserBinding
import kotlinx.coroutines.launch

class CategoryBrowserFragment : Fragment() {
    private var _binding: FragmentCategoryBrowserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    binding.etUrlBar.setText(it)
                }
            }
        }

        binding.webView.loadUrl("https://auctions.yahoo.co.jp/")

        // ★ 追加された「戻る」ボタンの処理
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                Toast.makeText(requireContext(), "これ以上戻れません", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSwitchYahoo.setOnClickListener {
            binding.webView.loadUrl("https://auctions.yahoo.co.jp/")
        }
        binding.btnSwitchFlea.setOnClickListener {
            binding.webView.loadUrl("https://paypayfleamarket.yahoo.co.jp/")
        }

        binding.btnSaveCategory.setOnClickListener {
            val currentUrl = binding.webView.url ?: ""
            binding.webView.evaluateJavascript("document.title") { titleResult ->
                val pageTitle = titleResult?.trim('"')?.replace("\\u0026", "&") ?: ""
                extractAndShowRegisterDialog(currentUrl, pageTitle)
            }
        }
    }

    private fun extractAndShowRegisterDialog(url: String, pageTitle: String) {
        var platform = "auction"
        var extractedValue = ""

        if (url.contains("auctions.yahoo.co.jp")) {
            platform = "auction"
            val match = Regex("auccat=([0-9]+)").find(url)
            if (match != null) {
                extractedValue = match.groupValues[1]
            } else {
                val pathMatch = Regex("category/([0-9]+)").find(url)
                if (pathMatch != null) extractedValue = pathMatch.groupValues[1]
            }
        } else if (url.contains("paypayfleamarket.yahoo.co.jp")) {
            platform = "fleamarket"
            val match = Regex("category/([0-9/]+)").find(url)
            if (match != null) {
                extractedValue = match.groupValues[1].trim('/')
            }
        }

        var suggestedName = pageTitle
            .replace(" - ヤフオク!", "")
            .replace(" - PayPayフリマ", "")
            .replace("のオークション", "")
            .trim()
        if (suggestedName.isBlank()) suggestedName = "新規カテゴリ"

        val scrollView = ScrollView(requireContext()).apply {
            setPadding(32, 24, 32, 24)
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvUrlInfo = TextView(requireContext()).apply {
            text = "【取得URL】\n$url"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 16)
        }

        val tvNameLabel = TextView(requireContext()).apply { text = "カテゴリ名（スクレイピング結果・修正可能）:" }
        val etName = EditText(requireContext()).apply {
            setText(suggestedName)
            setSelection(text.length)
        }

        val tvValLabel = TextView(requireContext()).apply { 
            text = "カテゴリID / パス（自動抽出）:"
            setPadding(0, 16, 0, 0)
        }
        val etValue = EditText(requireContext()).apply {
            setText(extractedValue)
        }

        layout.addView(tvUrlInfo)
        layout.addView(tvNameLabel)
        layout.addView(etName)
        layout.addView(tvValLabel)
        layout.addView(etValue)

        scrollView.addView(layout)

        AlertDialog.Builder(requireContext())
            .setTitle("カテゴリメモに登録")
            .setView(scrollView)
            .setPositiveButton("登録") { _, _ ->
                val name = etName.text.toString().trim()
                val value = etValue.text.toString().trim()

                if (name.isBlank() || value.isBlank()) {
                    Toast.makeText(requireContext(), "名称と値は必須です", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    db.categoryDao().insertCategory(
                        CategoryEntity(
                            platform = platform,
                            categoryName = name,
                            categoryValue = value
                        )
                    )
                    Toast.makeText(requireContext(), "カテゴリメモに保存しました！", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
