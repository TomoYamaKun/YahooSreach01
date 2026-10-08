//app/src/main/java/com/yahoosreach01/papa/SearchConditionAdapter.kt
//ver 1.01-12
package com.yahoosreach01.papa

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yahoosreach01.papa.databinding.ItemSearchConditionBinding

// 編集と削除のイベントを受け取るように変更
class SearchConditionAdapter(
    private val onEditClick: (SearchConditionEntity) -> Unit,
    private val onDeleteClick: (SearchConditionEntity) -> Unit
) : RecyclerView.Adapter<SearchConditionAdapter.ViewHolder>() {
    private var items = listOf<SearchConditionEntity>()

    fun submitList(newItems: List<SearchConditionEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemSearchConditionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchConditionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvPatternName.text = item.patternName
        holder.binding.tvSearchKeys.text = "検索: ${item.searchKeys} / 除外: ${item.excludeKeys}"
        
        val min = if (item.minPrice > 0) "${item.minPrice}円" else "下限なし"
        val max = if (item.maxPrice > 0) "${item.maxPrice}円" else "上限なし"
        holder.binding.tvPrice.text = "価格: $min 〜 $max"

        // ボタンのクリック処理
        holder.binding.btnEdit.setOnClickListener { onEditClick(item) }
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount() = items.size
}
