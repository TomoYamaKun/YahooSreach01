//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-167
package com.yahoosreach01.papa

import android.content.Context
import com.yahoosreach01.papa.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object SearchWorker {

    suspend fun executeSearch(context: Context, condition: SearchConditionEntity) {
        withContext(Dispatchers.IO) {

            val db = AppDatabase.getDatabase(context)
            val conditionId = condition.id.toLong()

            db.itemDao().clearActiveItemsForCondition(conditionId)

            val excludedItems = db.itemDao().getExcludedItemsListForCondition(conditionId)
            val excludedIds = excludedItems.map { it.itemId }.toSet()
            val excludedUrls = excludedItems.map { it.url }.toSet()

            val query = condition.searchKeys.trim()

            val excludePart =
                if (condition.excludeKeys.isNotBlank()) {
                    condition.excludeKeys.trim()
                        .split("\\s+".toRegex())
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { key ->
                            if (key.startsWith("-")) key else "-$key"
                        }
                } else {
                    ""
                }

            val fullQuery =
                if (excludePart.isNotBlank())
                    "$query $excludePart"
                else
                    query

            val encodedQuery =
                URLEncoder.encode(fullQuery, "UTF-8")

            val currentTime =
                System.currentTimeMillis()

            val fetchedItems = mutableListOf<ItemEntity>()

            // 1. Yahoo Auction Search
            if (condition.targetService != "fleamarket") {
                val yahooItems = YahooAuctionParser.parse(condition, encodedQuery, excludedIds, excludedUrls, currentTime)
                fetchedItems.addAll(yahooItems)
            }

            // 2. PayPay Flea Search
            if (condition.targetService != "auction") {
                val fleaItems = PayPayFleaParser.parse(condition, encodedQuery, excludedIds, excludedUrls, currentTime)
                fetchedItems.addAll(fleaItems)
            }

            // 3. Sort logic ("a": おすすめ/新着, "s": 価格安い順, "b": 入札件数多い, "e": 終了時間間近)
            val sortedItems = when (condition.sortOrder) {
                "s" -> fetchedItems.sortedBy { item ->
                    val p = if (item.currentPrice > 0) item.currentPrice else item.promptDecisionPrice
                    if (p > 0) p else Int.MAX_VALUE
                }
                "b" -> fetchedItems.sortedByDescending { item -> item.bidCount }
                "e" -> fetchedItems.sortedBy { item -> item.endTime ?: "-" }
                else -> fetchedItems.sortedByDescending { item -> item.createdAt } // "a" またはデフォルト
            }

            // 4. Database insert
            for (item in sortedItems) {
                db.itemDao().insertItem(item)
            }

            LogManager.d(
                "SearchWorker",
                "「${condition.patternName}」検索およびソート完了 (合計: ${sortedItems.size}件)"
            )
        }
    }

    fun buildSearchUrl(
        condition: SearchConditionEntity
    ): String {
        return ""
    }
}
