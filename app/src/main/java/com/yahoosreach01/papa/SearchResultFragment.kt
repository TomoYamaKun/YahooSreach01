//app/src/main/java/com/yahoosreach01/papa/SearchResultFragment.kt
//ver 1.01-146
package com.yahoosreach01.papa

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.yahoosreach01.papa.databinding.FragmentSearchResultBinding
import com.yahoosreach01.papa.utils.LogManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchResultFragment : Fragment() {
    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchResultAdapter
    private var currentSelectedConditionId: Long = -1L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())

        adapter = SearchResultAdapter(
            onItemClick = { item -> showItemDetailDialog(item) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // 初回起動時またはパターン選択時に独立表示するため、登録されている最初のパターンを自動選択するかダイアログを表示
        viewLifecycleOwner.lifecycleScope.launch {
            val conditions = db.searchConditionDao().getAllConditions().first()
            if (conditions.isNotEmpty()) {
                // デフォルトで最初の条件を選択
                selectCondition(conditions[0].id.toLong(), conditions[0].patternName)
            }
        }

        // 検索ボタン押下時の処理（検索実行 or 表示パターンの切り替え）
        binding.fabSearch.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val conditions = db.searchConditionDao().getAllConditions().first()
                if (conditions.isEmpty()) {
                    Toast.makeText(requireContext(), "先に「検索キー」タブで検索パターンを追加してください", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val patternNames = conditions.map { it.patternName }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("表示・検索するパターンを選択")
                    .setItems(patternNames) { _, which ->
                        val selectedCondition = conditions[which]
                        
                        AlertDialog.Builder(requireContext())
                            .setTitle("「${selectedCondition.patternName}」の操作")
                            .setMessage("新規に検索を実行しますか？それとも保存された結果を表示しますか？")
                            .setPositiveButton("新規検索する") { _, _ ->
                                Toast.makeText(requireContext(), "「${selectedCondition.patternName}」で検索中...", Toast.LENGTH_SHORT).show()
                                viewLifecycleOwner.lifecycleScope.launch {
                                    SearchWorker.executeSearch(requireContext(), selectedCondition)
                                    selectCondition(selectedCondition.id.toLong(), selectedCondition.patternName)
                                    Toast.makeText(requireContext(), "検索処理が完了しました", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("結果を表示する") { _, _ ->
                                selectCondition(selectedCondition.id.toLong(), selectedCondition.patternName)
                            }
                            .show()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }
    }

    private fun selectCondition(conditionId: Long, patternName: String) {
        currentSelectedConditionId = conditionId
        val db = AppDatabase.getDatabase(requireContext())
        
        Toast.makeText(requireContext(), "表示中: $patternName", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            db.itemDao().getActiveItemsForCondition(conditionId).collectLatest { items ->
                if (currentSelectedConditionId == conditionId) {
                    LogManager.d("SearchResultFragment", "「$patternName」のアクティブアイテム数: ${items.size}")
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun showItemDetailDialog(item: ItemEntity) {
        val scrollView = ScrollView(requireContext())
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        Glide.with(this).load(item.imageUrl).into(imageView)
        layout.addView(imageView)

        val priceText = if (item.source == "fleamarket") {
            "価格: ${item.promptDecisionPrice}円 / 送料: ${item.shippingInfo}"
        } else {
            val bidStr = if (item.bidCount > 0) " (入札:${item.bidCount}件)" else ""
            val promptStr = if (item.promptDecisionPrice > 0) " / 即決: ${item.promptDecisionPrice}円" else ""
            "現在: ${item.currentPrice}円$bidStr$promptStr / 送料: ${item.shippingInfo}"
        }

        val priceView = TextView(requireContext()).apply {
            text = priceText
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#D32F2F"))
            setPadding(0, 24, 0, 12)
        }
        layout.addView(priceView)

        val descView = TextView(requireContext()).apply {
            text = item.description ?: "詳細説明はありません"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#333333"))
        }
        layout.addView(descView)

        scrollView.addView(layout)

        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setView(scrollView)
            .setPositiveButton("アプリ内で開く") { _, _ ->
                openInAppWebView(item.url)
            }
            .setNeutralButton("除外する") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    // 検索条件ごとに独立して除外
                    AppDatabase.getDatabase(requireContext()).itemDao().excludeItem(item.itemId)
                    Toast.makeText(requireContext(), "この条件の除外リストに追加しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun openInAppWebView(url: String) {
        val activity = requireActivity()
        if (activity is MainActivity) {
            activity.loadUrlInTab(url)
        } else {
            val fragment = parentFragmentManager.findFragmentByTag("f1") as? YahooLoginFragment 
                ?: (parentFragmentManager.fragments.find { it is YahooLoginFragment } as? YahooLoginFragment)

            if (fragment != null) {
                fragment.loadItemUrl(url)
                Toast.makeText(requireContext(), "タブ内ブラウザで開きました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "ログイン・ブラウザタブが見つかりませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
