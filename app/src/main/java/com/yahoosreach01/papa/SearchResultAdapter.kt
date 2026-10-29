//app/src/main/java/com/yahoosreach01/papa/SearchResultAdapter.kt
//ver 1.02-03
package com.yahoosreach01.papa

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yahoosreach01.papa.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onItemClick: (ItemEntity) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {
    private var items = listOf<ItemEntity>()

    fun submitList(newItems: List<ItemEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // ★ お気に入りならタイトルに★を付与
        holder.binding.tvTitle.text = if (item.isFavorite) "★ ${item.title}" else item.title
        
        holder.binding.tvBadgeNew.visibility = if (item.isNew) View.VISIBLE else View.GONE

        // ★ 背景色の動的変更ロジック (お気に入りは薄い水色でハイライト)
        if (item.isFavorite) {
            holder.binding.root.setBackgroundColor(Color.parseColor("#E3F2FD"))
        } else if (item.source == "fleamarket") {
            // フリマは薄い黄色
            holder.binding.root.setBackgroundColor(Color.parseColor("#FFFDE7"))
        } else {
            // オークションの残り時間に応じた背景色判定
            var hoursLeft = 999.0
            val endTimeStr = item.endTime ?: "-"
            
            if (endTimeStr.contains("時間")) {
                val match = Regex("([0-9]+)\\s*時間").find(endTimeStr)
                if (match != null) {
                    hoursLeft = match.groupValues[1].toDoubleOrNull() ?: 999.0
                }
            } else if (endTimeStr.contains("分") && !endTimeStr.contains("日")) {
                hoursLeft = 0.5 // 1時間未満の目安
            }

            if (hoursLeft <= 1.0) {
                // 1時間以内：薄いピンク
                holder.binding.root.setBackgroundColor(Color.parseColor("#FFEBEE"))
            } else if (hoursLeft <= 12.0) {
                // 12時間以内：薄い緑
                holder.binding.root.setBackgroundColor(Color.parseColor("#E8F5E9"))
            } else {
                // 通常時：白
                holder.binding.root.setBackgroundColor(Color.parseColor("#FFFFFF"))
            }
        }

        // フリマとオークションの正確な区分け表示
        if (item.source == "fleamarket") {
            holder.binding.tvSource.text = "フリマ"
            holder.binding.tvSource.setBackgroundColor(Color.parseColor("#4CAF50"))

            val priceStr = if (item.promptDecisionPrice > 0) "${item.promptDecisionPrice}円" else "${item.currentPrice}円"
            holder.binding.tvCurrentPrice.text = "価格: $priceStr"
            holder.binding.tvPromptPrice.text = "送料: ${item.shippingInfo}"
            holder.binding.tvPromptPrice.visibility = View.VISIBLE

            holder.binding.tvEndTime.text = "終了: -"
        } else {
            holder.binding.tvSource.text = "オークション"
            holder.binding.tvSource.setBackgroundColor(Color.parseColor("#FF5722"))

            val bidStr = if (item.bidCount > 0) " (入札:${item.bidCount}件)" else " (入札:0件)"
            holder.binding.tvCurrentPrice.text = "現在: ${item.currentPrice}円$bidStr"

            val promptPart = if (item.promptDecisionPrice > 0) "即決: ${item.promptDecisionPrice}円 / " else ""
            holder.binding.tvPromptPrice.text = "${promptPart}送料: ${item.shippingInfo}"
            holder.binding.tvPromptPrice.visibility = View.VISIBLE

            val displayEndTime = if (!item.endTime.isNullOrBlank()) item.endTime else "-"
            holder.binding.tvEndTime.text = "終了: $displayEndTime"
        }

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.binding.ivItem)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
