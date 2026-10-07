//app/src/main/java/com/yahoosreach01/papa/SearchKeyFragment.kt
//ver 1.01-11
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
import com.yahoosreach01.papa.databinding.DialogAddConditionBinding
import com.yahoosreach01.papa.databinding.FragmentSearchKeyBinding
import kotlinx.coroutines.launch

class SearchKeyFragment : Fragment() {
    private var _binding: FragmentSearchKeyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchConditionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = SearchConditionAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val db = AppDatabase.getDatabase(requireContext())

        // DBからリアルタイムに条件一覧を取得してリストに反映
        viewLifecycleOwner.lifecycleScope.launch {
            db.searchConditionDao().getAllConditions().collect { conditions ->
                adapter.submitList(conditions)
            }
        }

        // 右下の＋ボタンで入力ダイアログを表示
        binding.fabAdd.setOnClickListener {
            showAddDialog(db)
        }
    }

    private fun showAddDialog(db: AppDatabase) {
        val dialogBinding = DialogAddConditionBinding.inflate(layoutInflater)
        
        AlertDialog.Builder(requireContext())
            .setTitle("検索パターン追加")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val patternName = dialogBinding.etPatternName.text.toString()
                if (patternName.isBlank()) {
                    Toast.makeText(requireContext(), "パターン名は必須です", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val searchKeys = dialogBinding.etSearchKeys.text.toString()
                val excludeKeys = dialogBinding.etExcludeKeys.text.toString()
                val minPrice = dialogBinding.etMinPrice.text.toString().toIntOrNull() ?: 0
                val maxPrice = dialogBinding.etMaxPrice.text.toString().toIntOrNull() ?: 0
                val target = if (dialogBinding.cbYahoo.isChecked) "both" else "fleamarket"
                
                val entity = SearchConditionEntity(
                    patternName = patternName,
                    searchKeys = searchKeys,
                    excludeKeys = excludeKeys,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    categories = "",
                    targetService = target
                )
                
                viewLifecycleOwner.lifecycleScope.launch {
                    db.searchConditionDao().insertCondition(entity)
                    Toast.makeText(requireContext(), "保存しました", Toast.LENGTH_SHORT).show()
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
