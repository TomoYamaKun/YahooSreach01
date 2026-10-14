//app/src/main/java/com/yahoosreach01/papa/SearchResultFragment.kt
//ver 1.02-00
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchResultFragment : Fragment() {
    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchResultAdapter
    private var collectJob: Job? = null

    companion object {
        // 画面復帰時のリフレッシュ（強制リセット）を防ぐために状態を保持
        private var currentSelectedConditionId: Long = -1L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogManager.d("SearchResultFragment", "onViewCreated 開始")

        val db = AppDatabase.getDatabase(requireContext())

        adapter = SearchResultAdapter(
            onItemClick = { item -> showItemDetailDialog(item) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // ★ 既に選択中の状態がある場合は初期ロードによるリセットを行わず維持する
        if (currentSelectedConditionId == -1L) {
            viewLifecycleOwner.lifecycleScope.launch {
                val conditions = db.searchConditionDao().getAllConditions().first()
                LogManager.d("SearchResultFragment", "初期ロード 検索パターン数: ${conditions.size}")
                if (conditions.isNotEmpty()) {
                    selectCondition(conditions[0].id.toLong(), conditions[0].patternName)
                } else {
                    LogManager.d("SearchResultFragment", "検索パターンが登録されていません")
                }
            }
        } else {
            LogManager.d("SearchResultFragment", "既存の選択状態を維持します (conditionId: $currentSelectedConditionId)")
            viewLifecycleOwner.lifecycleScope.launch {
                val conditions = db.searchConditionDao().getAllConditions().first()
                val current = conditions.find { it.id.toLong() == currentSelectedConditionId }
                if (current != null) {
                    selectCondition(current.id.toLong(), current.patternName)
                } else if (conditions.isNotEmpty()) {
                    selectCondition(conditions[0].id.toLong(), conditions[0].patternName)
                }
            }
        }

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
                                LogManager.d("SearchResultFragment", "新規検索実行開始: ${selectedCondition.patternName}")
                                viewLifecycleOwner.lifecycleScope.launch {
                                    SearchWorker.executeSearch(requireContext(), selectedCondition)
                                    selectCondition(selectedCondition.id.toLong(), selectedCondition.patternName)
                                    Toast.makeText(requireContext(), "検索処理が完了しました", Toast.LENGTH_SHORT).show()
                                    LogManager.d("SearchResultFragment", "新規検索および表示切り替え完了")
                                }
                            }
                            .setNegativeButton("結果を表示する") { _, _ ->
                                LogManager.d("SearchResultFragment", "保存結果表示選択: ${selectedCondition.patternName}")
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
        LogManager.d("SearchResultFragment", "selectCondition 呼び出し: patternName=$patternName, conditionId=$conditionId")

        collectJob?.cancel()

        collectJob = viewLifecycleOwner.lifecycleScope.launch {
            db.itemDao().getActiveItemsForCondition(conditionId).collectLatest { items ->
                if (currentSelectedConditionId == conditionId) {
                    LogManager.d("SearchResultFragment", "「$patternName」のアクティブアイテム数受信: ${items.size}件")
                    adapter.submitList(items)
                    LogManager.d("SearchResultFragment", "「$patternName」のRecyclerViewへのリスト適用完了")
                } else {
                    LogManager.d("SearchResultFragment", "conditionIdが変更されているためアイテム適用をスキップ (current: $currentSelectedConditionId, received for: $conditionId)")
                }
            }
        }
    }

    private fun showItemDetailDialog(item: ItemEntity) {
        LogManager.d("SearchResultFragment", "アイテム詳細ダイアログ表示: ${item.title}")
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
                LogManager.d("SearchResultFragment", "詳細ダイアログ [アプリ内で開く] タップ: ${item.url}")
                openInAppWebView(item.url)
            }
            .setNeutralButton("除外する") { _, _ ->
                LogManager.d("SearchResultFragment", "詳細ダイアログ [除外する] タップ: ${item.itemId}")
                viewLifecycleOwner.lifecycleScope.launch {
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
        LogManager.d("SearchResultFragment", "onDestroyView")
        _binding = null
    }
}
