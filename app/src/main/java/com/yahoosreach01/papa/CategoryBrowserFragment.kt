//app/src/main/java/com/yahoosreach01/papa/CategoryBrowserFragment.kt
//ver 1.01-156
package com.yahoosreach01.papa

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
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

        binding.btnSwitchYahoo.setOnClickListener {
            binding.webView.loadUrl("https://auctions.yahoo.co.jp/")
        }
        binding.btnSwitchFlea.setOnClickListener {
            binding.webView.loadUrl("https://paypayfleamarket.yahoo.co.jp/")
        }

        binding.btnSaveCategory.setOnClickListener {
            val currentUrl = binding.webView.url ?: ""
            extractAndSaveCategory(currentUrl)
        }
    }

    private fun extractAndSaveCategory(url: String) {
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

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val etName = EditText(requireContext()).apply {
            hint = "カテゴリ名（例: DDR4メモリ）"
        }
        val etValue = EditText(requireContext()).apply {
            hint = "カテゴリID / パス"
            setText(extractedValue)
        }

        layout.addView(etName)
        layout.addView(etValue)

        AlertDialog.Builder(requireContext())
            .setTitle("カテゴリメモに登録")
            .setView(layout)
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
