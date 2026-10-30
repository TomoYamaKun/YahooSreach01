//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.02-42
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

            val existingItems = db.itemDao().getItemsListForCondition(conditionId)
            val favoriteMap = existingItems.associate { it.itemId to it.isFavorite }

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

            val serviceType = condition.targetService.lowercase().trim()
            LogManager.d("SearchWorker", "実行対象サービス判定: serviceType='$serviceType' (patternName=${condition.patternName})")

            // 1. Yahoo Auction Search
            if (serviceType != "fleamarket") {
                try {
                    val yahooItems = YahooAuctionParser.parse(context, condition, encodedQuery, excludedIds, excludedUrls, currentTime)
                    if (yahooItems.isNotEmpty()) {
                        fetchedItems.addAll(yahooItems)
                        LogManager.d("SearchWorker", "ヤフオク取得反映成功: ${yahooItems.size}件")
                    } else {
                        LogManager.d("SearchWorker", "ヤフオク取得件数: 0件 (パース結果空)")
                    }
                } catch (e: Exception) {
                    LogManager.e("SearchWorker", "ヤフオクパース実行時例外", e)
                }
            }

            // 2. PayPay Flea Search
            if (serviceType != "auction") {
                try {
                    val fleaItems = PayPayFleaParser.parse(context, condition, encodedQuery, excludedIds, excludedUrls, currentTime)
                    if (fleaItems.isNotEmpty()) {
                        fetchedItems.addAll(fleaItems)
                        LogManager.d("SearchWorker", "フリマ取得反映成功: ${fleaItems.size}件")
                    } else {
                        LogManager.d("SearchWorker", "フリマ取得件数: 0件 (パース結果空)")
                    }
                } catch (e: Exception) {
                    LogManager.e("SearchWorker", "フリマパース実行時例外", e)
                }
            }

            LogManager.d("SearchWorker", "合算総取得件数: ${fetchedItems.size}件")

            // 3. 終了・SoldOutの自動除外 ＋ お気に入り引き継ぎ
            val validItems = mutableListOf<ItemEntity>()
            for (item in fetchedItems) {
                val isEndedOrSoldOut = if (item.source == "auction") {
                    item.endTime == "終了" || item.endTime?.contains("オークションは終了しました") == true
                } else {
                    val effectivePrice = if (item.promptDecisionPrice > 0) item.promptDecisionPrice else item.currentPrice
                    item.shippingInfo?.contains("sold", ignoreCase = true) == true || effectivePrice == 0
                }

                if (isEndedOrSoldOut) {
                    continue
                }

                val isFav = favoriteMap[item.itemId] ?: false
                val finalItem = if (isFav) item.copy(isFavorite = true) else item
                validItems.add(finalItem)
            }

            // 4. Sort logic
            val sortedItems = when (condition.sortOrder) {
                "s" -> validItems.sortedBy { item ->
                    val p = if (item.currentPrice > 0) item.currentPrice else item.promptDecisionPrice
                    if (p > 0) p else Int.MAX_VALUE
                }
                "b" -> validItems.sortedByDescending { item -> item.bidCount }
                "e" -> validItems.sortedBy { item -> item.endTime ?: "-" }
                else -> validItems.sortedByDescending { item -> item.createdAt }
            }

            // 5. Database insert
            for (item in sortedItems) {
                db.itemDao().insertItem(item)
            }

            LogManager.d(
                "SearchWorker",
                "「${condition.patternName}」検索およびソート完了 (有効件数: ${sortedItems.size}件)"
            )
        }
    }

    fun buildSearchUrl(
        condition: SearchConditionEntity
    ): String {
        return ""
    }
}
