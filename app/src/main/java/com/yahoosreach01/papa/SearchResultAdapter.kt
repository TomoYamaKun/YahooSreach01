//app/src/main/java/com/yahoosreach01/papa/SearchResultAdapter.kt
//ver 1.01-25
package com.yahoosreach01.papa

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
        holder.binding.tvTitle.text = item.title
        
        holder.binding.tvBadgeNew.visibility = if (item.isNew) View.VISIBLE else View.GONE

        // フリマとオークションの正確な区分け表示
        if (item.source == "fleamarket") {
            holder.binding.tvSource.text = "フリマ"
            holder.binding.tvSource.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))

            val priceStr = if (item.promptDecisionPrice > 0) "${item.promptDecisionPrice}円" else "${item.currentPrice}円"
            holder.binding.tvCurrentPrice.text = "価格: $priceStr"
            holder.binding.tvPromptPrice.text = "送料: ${item.shippingInfo}"
            holder.binding.tvPromptPrice.visibility = View.VISIBLE
        } else {
            holder.binding.tvSource.text = "オークション"
            holder.binding.tvSource.setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))

            // オークション：現在値 ＋ 入札数 ＋ 即決値(あれば) ＋ 送料
            val bidStr = if (item.bidCount > 0) " (入札:${item.bidCount}件)" else " (入札:0件)"
            holder.binding.tvCurrentPrice.text = "現在: ${item.currentPrice}円$bidStr"

            val promptPart = if (item.promptDecisionPrice > 0) "即決: ${item.promptDecisionPrice}円 / " else ""
            holder.binding.tvPromptPrice.text = "${promptPart}送料: ${item.shippingInfo}"
            holder.binding.tvPromptPrice.visibility = View.VISIBLE
        }

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.binding.ivItem)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
