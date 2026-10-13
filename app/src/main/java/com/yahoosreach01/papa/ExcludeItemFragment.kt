//app/src/main/java/com/yahoosreach01/papa/ExcludeItemFragment.kt
//ver 1.01-148
package com.yahoosreach01.papa

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yahoosreach01.papa.databinding.FragmentSearchKeyBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExcludeItemFragment : Fragment() {
    private var _binding: FragmentSearchKeyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchResultAdapter
    private var collectJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // ★ 隠していたFABを表示状態にする（アイコンは必要に応じて検索結果画面と同様に虫眼鏡として機能）
        binding.fabAdd.visibility = View.VISIBLE
        binding.fabAdd.setImageResource(android.R.drawable.ic_menu_search) // 虫眼鏡アイコン等に設定

        val db = AppDatabase.getDatabase(requireContext())

        adapter = SearchResultAdapter(
            onItemClick = { item ->
                AlertDialog.Builder(requireContext())
                    .setTitle("除外の解除")
                    .setMessage("「${item.title}」を検索結果に復帰させますか？")
                    .setPositiveButton("復帰する") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            db.itemDao().restoreItem(item.itemId)
                            Toast.makeText(requireContext(), "検索結果に復帰しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // 初回起動時は登録されている最初のパターンの除外リストをデフォルトで自動表示
        viewLifecycleOwner.lifecycleScope.launch {
            val conditions = db.searchConditionDao().getAllConditions().first()
            if (conditions.isNotEmpty()) {
                loadExcludedItems(conditions[0].id.toLong(), conditions[0].patternName)
            }
        }

        // ★ 虫眼鏡ボタン（FAB）タップで、いつでも除外リストを表示するパターンを切り替えられるようにする
        binding.fabAdd.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val conditions = db.searchConditionDao().getAllConditions().first()
                if (conditions.isEmpty()) {
                    Toast.makeText(requireContext(), "検索パターンが登録されていません", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val patternNames = conditions.map { it.patternName }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("確認する除外パターンを選択")
                    .setItems(patternNames) { _, which ->
                        val selected = conditions[which]
                        loadExcludedItems(selected.id.toLong(), selected.patternName)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        }
    }

    private fun loadExcludedItems(conditionId: Long, patternName: String) {
        val db = AppDatabase.getDatabase(requireContext())
        Toast.makeText(requireContext(), "除外表示: $patternName", Toast.LENGTH_SHORT).show()
        
        // 既存の監視ジョブをキャンセルして新しい条件で購読
        collectJob?.cancel()
        collectJob = viewLifecycleOwner.lifecycleScope.launch {
            db.itemDao().getExcludedItemsForCondition(conditionId).collectLatest { items ->
                adapter.submitList(items)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
