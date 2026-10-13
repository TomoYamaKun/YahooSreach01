//app/src/main/java/com/yahoosreach01/papa/SearchKeyFragment.kt
//ver 1.01-165
package com.yahoosreach01.papa

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yahoosreach01.papa.databinding.DialogAddConditionBinding
import com.yahoosreach01.papa.databinding.FragmentSearchKeyBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SearchKeyFragment : Fragment() {
    private var _binding: FragmentSearchKeyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchConditionAdapter

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                viewLifecycleOwner.lifecycleScope.launch {
                    val conditions = db.searchConditionDao().getAllConditions().first()
                    val sb = StringBuilder()
                    sb.append("--- CONDITIONS ---\n")
                    for (c in conditions) {
                        sb.append("${c.id}|${c.patternName}|${c.searchKeys}|${c.excludeKeys}|${c.minPrice}|${c.maxPrice}|${c.targetService}|${c.sortOrder}\n")
                    }
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                            writer.write(sb.toString())
                            writer.flush()
                        }
                    }
                    Toast.makeText(requireContext(), "検索条件をエクスポートしました", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "エクスポート失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val db = AppDatabase.getDatabase(requireContext())

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
            showDialog(db, null)
        }

        binding.recyclerView.setOnLongClickListener {
            showManagementMenu(db)
            true
        }
    }

    private fun showManagementMenu(db: AppDatabase) {
        val options = arrayOf("登録カテゴリの管理（一覧・編集・削除）", "検索条件ファイル出力 (エクスポート)")
        AlertDialog.Builder(requireContext())
            .setTitle("データ・カテゴリ管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCategoryManagerDialog(db)
                    1 -> exportLauncher.launch("search_conditions_backup.txt")
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showCategoryManagerDialog(db: AppDatabase) {
        viewLifecycleOwner.lifecycleScope.launch {
            val categories = db.categoryDao().getAllCategoriesList()
            if (categories.isEmpty()) {
                Toast.makeText(requireContext(), "登録されたカテゴリがありません", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val items = categories.map { "[${it.platform}] ${it.categoryName} (${it.categoryValue})" }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("登録カテゴリ一覧（タップで編集・削除）")
                .setItems(items) { _, index ->
                    val selectedCat = categories[index]
                    showEditOrDeleteCategoryDialog(db, selectedCat)
                }
                .setPositiveButton("閉じる", null)
                .show()
        }
    }

    private fun showEditOrDeleteCategoryDialog(db: AppDatabase, cat: CategoryEntity) {
        val options = arrayOf("編集する", "削除する")
        AlertDialog.Builder(requireContext())
            .setTitle("カテゴリの操作: ${cat.categoryName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val layout = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(48, 32, 48, 32)
                        }
                        val etName = EditText(requireContext()).apply { setText(cat.categoryName) }
                        val etVal = EditText(requireContext()).apply { setText(cat.categoryValue) }
                        layout.addView(etName)
                        layout.addView(etVal)

                        AlertDialog.Builder(requireContext())
                            .setTitle("カテゴリ編集")
                            .setView(layout)
                            .setPositiveButton("更新") { _, _ ->
                                val newName = etName.text.toString().trim()
                                val newVal = etVal.text.toString().trim()
                                if (newName.isNotBlank() && newVal.isNotBlank()) {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        db.categoryDao().insertCategory(cat.copy(categoryName = newName, categoryValue = newVal))
                                        Toast.makeText(requireContext(), "更新しました", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("キャンセル", null)
                            .show()
                    }
                    1 -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("確認")
                            .setMessage("「${cat.categoryName}」を削除しますか？")
                            .setPositiveButton("削除") { _, _ ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    db.categoryDao().deleteCategory(cat)
                                    Toast.makeText(requireContext(), "削除しました", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("キャンセル", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showDialog(db: AppDatabase, existingCondition: SearchConditionEntity?) {
        val dialogBinding = DialogAddConditionBinding.inflate(layoutInflater)
        
        val sortLabels = arrayOf("新着順 / おすすめ", "価格が安い順", "入札件数が多い順", "残り時間が短い順")
        val sortValues = arrayOf("a", "s", "b", "e")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)
        dialogBinding.spinnerSort.adapter = spinnerAdapter

        existingCondition?.let {
            dialogBinding.etPatternName.setText(it.patternName)
            dialogBinding.etSearchKeys.setText(it.searchKeys)
            dialogBinding.etExcludeKeys.setText(it.excludeKeys)
            dialogBinding.etMinPrice.setText(if (it.minPrice > 0) it.minPrice.toString() else "")
            dialogBinding.etMaxPrice.setText(if (it.maxPrice > 0) it.maxPrice.toString() else "")
            dialogBinding.etCategories.setText(it.categories)
            dialogBinding.etExcludeCategories.setText(it.excludeCategories)
            dialogBinding.cbYahoo.isChecked = (it.targetService == "both" || it.targetService == "auction")
            
            val sortIndex = sortValues.indexOf(it.sortOrder)
            if (sortIndex >= 0) dialogBinding.spinnerSort.setSelection(sortIndex)
        }

        // ★ 対象カテゴリの「選択」ボタン
        dialogBinding.btnSelectCategory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val cats = db.categoryDao().getAllCategoriesList()
                if (cats.isNotEmpty()) {
                    val catNames = cats.map { "[${it.platform}] ${it.categoryName} (${it.categoryValue})" }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle("対象カテゴリを選択")
                        .setItems(catNames) { _, idx ->
                            val currentText = dialogBinding.etCategories.text.toString()
                            val newVal = cats[idx].categoryValue
                            dialogBinding.etCategories.setText(if (currentText.isBlank()) newVal else "$currentText,$newVal")
                        }
                        .show()
                } else {
                    Toast.makeText(requireContext(), "保存されたカテゴリがありません。「カテゴリ」タブで先に登録してください", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ★ 除外カテゴリの「選択」ボタン
        dialogBinding.btnSelectExcludeCategory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val cats = db.categoryDao().getAllCategoriesList()
                if (cats.isNotEmpty()) {
                    val catNames = cats.map { "[${it.platform}] ${it.categoryName} (${it.categoryValue})" }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle("除外カテゴリを選択")
                        .setItems(catNames) { _, idx ->
                            val currentText = dialogBinding.etExcludeCategories.text.toString()
                            val newVal = cats[idx].categoryValue
                            dialogBinding.etExcludeCategories.setText(if (currentText.isBlank()) newVal else "$currentText,$newVal")
                        }
                        .show()
                } else {
                    Toast.makeText(requireContext(), "保存されたカテゴリがありません", Toast.LENGTH_SHORT).show()
                }
            }
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
                val categories = dialogBinding.etCategories.text.toString()
                val excludeCategories = dialogBinding.etExcludeCategories.text.toString()
                val target = if (dialogBinding.cbYahoo.isChecked) "both" else "fleamarket"
                val selectedSort = sortValues[dialogBinding.spinnerSort.selectedItemPosition]
                
                val entity = SearchConditionEntity(
                    id = existingCondition?.id ?: 0, 
                    patternName = patternName,
                    searchKeys = searchKeys,
                    excludeKeys = excludeKeys,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    categories = categories,
                    excludeCategories = excludeCategories,
                    targetService = target,
                    sortOrder = selectedSort
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
