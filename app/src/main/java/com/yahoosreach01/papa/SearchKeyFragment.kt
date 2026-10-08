//app/src/main/java/com/yahoosreach01/papa/SearchKeyFragment.kt
//ver 1.01-12
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
        
        val db = AppDatabase.getDatabase(requireContext())

        // アダプター生成時に編集と削除の処理を渡す
        adapter = SearchConditionAdapter(
            onEditClick = { condition -> showDialog(db, condition) },
            onDeleteClick = { condition -> deleteCondition(db, condition) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            db.searchConditionDao().getAllConditions().collect { conditions ->
                adapter.submitList(conditions)
            }
        }

        binding.fabAdd.setOnClickListener {
            showDialog(db, null) // nullを渡すと新規作成モード
        }
    }

    // 新規と編集を共通のダイアログで処理
    private fun showDialog(db: AppDatabase, existingCondition: SearchConditionEntity?) {
        val dialogBinding = DialogAddConditionBinding.inflate(layoutInflater)
        
        // 既存データがあればセット（編集モード）
        existingCondition?.let {
            dialogBinding.etPatternName.setText(it.patternName)
            dialogBinding.etSearchKeys.setText(it.searchKeys)
            dialogBinding.etExcludeKeys.setText(it.excludeKeys)
            dialogBinding.etMinPrice.setText(if (it.minPrice > 0) it.minPrice.toString() else "")
            dialogBinding.etMaxPrice.setText(if (it.maxPrice > 0) it.maxPrice.toString() else "")
            dialogBinding.cbYahoo.isChecked = (it.targetService == "both")
        }

        val title = if (existingCondition == null) "検索パターン追加" else "検索パターン編集"

        AlertDialog.Builder(requireContext())
            .setTitle(title)
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
                
                // existingCondition がnullなら新規(id=0で自動採番)、既存ならそのIDを引継ぐ
                val entity = SearchConditionEntity(
                    id = existingCondition?.id ?: 0, 
                    patternName = patternName,
                    searchKeys = searchKeys,
                    excludeKeys = excludeKeys,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    categories = "",
                    targetService = target
                )
                
                viewLifecycleOwner.lifecycleScope.launch {
                    if (existingCondition == null) {
                        db.searchConditionDao().insertCondition(entity)
                    } else {
                        db.searchConditionDao().updateCondition(entity)
                    }
                    Toast.makeText(requireContext(), "保存しました", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun deleteCondition(db: AppDatabase, condition: SearchConditionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("確認")
            .setMessage("「${condition.patternName}」を削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.searchConditionDao().deleteCondition(condition)
                    Toast.makeText(requireContext(), "削除しました", Toast.LENGTH_SHORT).show()
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
