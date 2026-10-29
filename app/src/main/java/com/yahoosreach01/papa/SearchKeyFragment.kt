//app/src/main/java/com/yahoosreach01/papa/SearchKeyFragment.kt
//ver 1.02-03
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
import org.json.JSONArray
import org.json.JSONObject
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
                    val conditions = db.searchConditionDao().getAllConditionsList()
                    val categories = db.categoryDao().getAllCategoriesList()
                    val items = db.itemDao().getAllItemsList()

                    val root = JSONObject().apply {
                        put("version", Constants.APP_VERSION)
                        put("timestamp", System.currentTimeMillis())

                        val condArray = JSONArray()
                        for (c in conditions) {
                            condArray.put(JSONObject().apply {
                                put("id", c.id)
                                put("patternName", c.patternName)
                                put("searchKeys", c.searchKeys)
                                put("excludeKeys", c.excludeKeys)
                                put("minPrice", c.minPrice)
                                put("maxPrice", c.maxPrice)
                                put("categories", c.categories)
                                put("excludeCategories", c.excludeCategories)
                                put("targetService", c.targetService)
                                put("sortOrder", c.sortOrder)
                            })
                        }
                        put("conditions", condArray)

                        val catArray = JSONArray()
                        for (cat in categories) {
                            catArray.put(JSONObject().apply {
                                put("platform", cat.platform)
                                put("categoryName", cat.categoryName)
                                put("categoryValue", cat.categoryValue)
                            })
                        }
                        put("categories", catArray)

                        val itemArray = JSONArray()
                        for (it in items) {
                            itemArray.put(JSONObject().apply {
                                put("itemId", it.itemId)
                                put("conditionId", it.conditionId)
                                put("title", it.title)
                                put("url", it.url)
                                put("imageUrl", it.imageUrl)
                                put("currentPrice", it.currentPrice)
                                put("promptDecisionPrice", it.promptDecisionPrice)
                                put("bidCount", it.bidCount)
                                put("shippingInfo", it.shippingInfo)
                                put("source", it.source)
                                put("description", it.description)
                                put("isNew", it.isNew)
                                put("isExcluded", it.isExcluded)
                                put("isFavorite", it.isFavorite)
                                put("endTime", it.endTime)
                                put("createdAt", it.createdAt)
                            })
                        }
                        put("items", itemArray)
                    }

                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                            writer.write(root.toString(2))
                            writer.flush()
                        }
                    }
                    Toast.makeText(requireContext(), "全データのバックアップに成功しました", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "エクスポート失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val jsonString = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
                
                val root = JSONObject(jsonString)
                val db = AppDatabase.getDatabase(requireContext())

                viewLifecycleOwner.lifecycleScope.launch {
                    val condArray = root.optJSONArray("conditions") ?: JSONArray()
                    val newConds = mutableListOf<SearchConditionEntity>()
                    for (i in 0 until condArray.length()) {
                        val obj = condArray.getJSONObject(i)
                        newConds.add(
                            SearchConditionEntity(
                                id = obj.optInt("id", 0),
                                patternName = obj.optString("patternName"),
                                searchKeys = obj.optString("searchKeys"),
                                excludeKeys = obj.optString("excludeKeys"),
                                minPrice = obj.optInt("minPrice"),
                                maxPrice = obj.optInt("maxPrice"),
                                categories = obj.optString("categories"),
                                excludeCategories = obj.optString("excludeCategories"),
                                targetService = obj.optString("targetService", "both"),
                                sortOrder = obj.optString("sortOrder", "a")
                            )
                        )
                    }
                    db.searchConditionDao().deleteAllConditions()
                    db.searchConditionDao().insertAllConditions(newConds)

                    val catArray = root.optJSONArray("categories") ?: JSONArray()
                    val newCats = mutableListOf<CategoryEntity>()
                    for (i in 0 until catArray.length()) {
                        val obj = catArray.getJSONObject(i)
                        newCats.add(
                            CategoryEntity(
                                platform = obj.optString("platform"),
                                categoryName = obj.optString("categoryName"),
                                categoryValue = obj.optString("categoryValue")
                            )
                        )
                    }
                    db.categoryDao().deleteAllCategories()
                    db.categoryDao().insertAllCategories(newCats)

                    val itemArray = root.optJSONArray("items") ?: JSONArray()
                    val newItems = mutableListOf<ItemEntity>()
                    for (i in 0 until itemArray.length()) {
                        val obj = itemArray.getJSONObject(i)
                        newItems.add(
                            ItemEntity(
                                itemId = obj.optString("itemId"),
                                conditionId = obj.optLong("conditionId"),
                                title = obj.optString("title"),
                                url = obj.optString("url"),
                                imageUrl = obj.optString("imageUrl"),
                                localImagePath = null,
                                currentPrice = obj.optInt("currentPrice"),
                                promptDecisionPrice = obj.optInt("promptDecisionPrice"),
                                bidCount = obj.optInt("bidCount"),
                                shippingInfo = obj.optString("shippingInfo"),
                                source = obj.optString("source"),
                                description = obj.optString("description"),
                                isNew = obj.optBoolean("isNew"),
                                isExcluded = obj.optBoolean("isExcluded"),
                                isFavorite = obj.optBoolean("isFavorite"),
                                endTime = obj.optString("endTime"),
                                createdAt = obj.optLong("createdAt")
                            )
                        )
                    }
                    db.itemDao().insertAllItems(newItems)

                    Toast.makeText(requireContext(), "バックアップから全データを正常に復元しました！", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "インポート失敗: ${e.message}", Toast.LENGTH_LONG).show()
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
        val options = arrayOf("登録カテゴリの管理（一覧・編集・削除）", "全データ バックアップ出力 (エクスポート)", "全データ バックアップ復元 (インポート)")
        AlertDialog.Builder(requireContext())
            .setTitle("データ・カテゴリ管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCategoryManagerDialog(db)
                    1 -> exportLauncher.launch("yahoo_search_full_backup_${Constants.APP_VERSION}.json")
                    2 -> importLauncher.launch(arrayOf("application/json", "*/*"))
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

        if (existingCondition != null) {
            dialogBinding.etPatternName.setText(existingCondition.patternName)
            dialogBinding.etSearchKeys.setText(existingCondition.searchKeys)
            dialogBinding.etExcludeKeys.setText(existingCondition.excludeKeys)
            dialogBinding.etMinPrice.setText(if (existingCondition.minPrice > 0) existingCondition.minPrice.toString() else "")
            dialogBinding.etMaxPrice.setText(if (existingCondition.maxPrice > 0) existingCondition.maxPrice.toString() else "")
            dialogBinding.etCategories.setText(existingCondition.categories)
            dialogBinding.etExcludeCategories.setText(existingCondition.excludeCategories)
            
            val isYahoo = (existingCondition.targetService == "both" || existingCondition.targetService == "auction")
            val isFlea = (existingCondition.targetService == "both" || existingCondition.targetService == "fleamarket")
            dialogBinding.cbTargetYahoo.isChecked = isYahoo
            dialogBinding.cbTargetFlea.isChecked = isFlea
            
            val sortIndex = sortValues.indexOf(existingCondition.sortOrder)
            if (sortIndex >= 0) dialogBinding.spinnerSort.setSelection(sortIndex)
        } else {
            dialogBinding.cbTargetYahoo.isChecked = true
            dialogBinding.cbTargetFlea.isChecked = false
        }

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
                    Toast.makeText(requireContext(), "保存されたカテゴリがありません", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                
                val yahooChecked = dialogBinding.cbTargetYahoo.isChecked
                val fleaChecked = dialogBinding.cbTargetFlea.isChecked
                
                if (!yahooChecked && !fleaChecked) {
                    Toast.makeText(requireContext(), "ヤフオクまたはフリマのどちらかを選択してください", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val target = when {
                    yahooChecked && fleaChecked -> "both"
                    yahooChecked -> "auction"
                    else -> "fleamarket"
                }

                val searchKeys = dialogBinding.etSearchKeys.text.toString()
                val excludeKeys = dialogBinding.etExcludeKeys.text.toString()
                val minPrice = dialogBinding.etMinPrice.text.toString().toIntOrNull() ?: 0
                val maxPrice = dialogBinding.etMaxPrice.text.toString().toIntOrNull() ?: 0
                val categories = dialogBinding.etCategories.text.toString()
                val excludeCategories = dialogBinding.etExcludeCategories.text.toString()
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
