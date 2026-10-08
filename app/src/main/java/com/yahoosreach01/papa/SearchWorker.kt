//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-27
package com.yahoosreach01.papa

import android.content.Context
import com.yahoosreach01.papa.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

object SearchWorker {

    suspend fun executeSearch(context: Context, condition: SearchConditionEntity) {
        withContext(Dispatchers.IO) {
            try {
                val url = buildSearchUrl(condition)
                LogManager.d("SearchWorker", "検索URL: $url")

                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get()

                val items = doc.select(".Product, li.clst")
                LogManager.d("SearchWorker", "取得件数: ${items.size}")

                val db = AppDatabase.getDatabase(context)
                val currentTime = System.currentTimeMillis()

                for (element in items) {
                    try {
                        val titleEl = element.select(".Product__titleLink, a.thm")
                        val title = titleEl.text() ?: continue
                        val itemUrl = titleEl.attr("href")
                        
                        if (itemUrl.isBlank()) continue
                        val itemId = if (itemUrl.contains("/auction/")) {
                            itemUrl.substringAfter("/auction/").substringBefore("?")
                        } else {
                            itemUrl.hashCode().toString()
                        }

                        val imgEl = element.select(".Product__image img, img")
                        val imageUrl = imgEl.attr("data-src").ifEmpty { imgEl.attr("src") }

                        val cardText = element.text()
                        val isFleamarket = itemUrl.contains("fleamarket") || cardText.contains("フリマ") || cardText.contains("定額")
                        val source = if (isFleamarket) "fleamarket" else "auction"

                        var currentPrice = 0
                        var promptPrice = 0
                        var bidCount = 0

                        // 【価格抽出の完全分離ロジック】カード内テキストから「現在」と「即決」の金額を個別に正確に抽出
                        val currentMatch = Regex("現在[:\\s]*([0-9,]+)円").find(cardText)
                        if (currentMatch != null) {
                            currentPrice = currentMatch.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                        }

                        val promptMatch = Regex("即決[:\\s]*([0-9,]+)円").find(cardText)
                        if (promptMatch != null) {
                            promptPrice = promptMatch.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                        }

                        // もし上記で取れなかった場合のフォールバック（従来の単一価格要素からの取得）
                        if (currentPrice == 0 && promptPrice == 0) {
                            val priceElementText = element.select(".Product__priceValue, .prc").text()
                            val rawMatch = Regex("([0-9,]+)").find(priceElementText)
                            val rawPrice = rawMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
                            if (source == "fleamarket") {
                                promptPrice = rawPrice
                            } else {
                                currentPrice = rawPrice
                            }
                        } else if (source == "fleamarket" && promptPrice == 0) {
                            promptPrice = currentPrice
                            currentPrice = 0
                        }

                        // 入札数の抽出
                        val bidMatch = Regex("([0-9]+)件").find(cardText)
                        bidCount = bidMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                        // 送料の抽出
                        val shippingInfo = when {
                            cardText.contains("送料無料") || cardText.contains("送料込") -> "送料無料"
                            cardText.contains("着払い") -> "着払い"
                            cardText.contains("出品者負担") -> "送料無料(出品者負担)"
                            else -> {
                                val shipMatch = Regex("送料[:\\s]*([0-9,]+)円").find(cardText)
                                if (shipMatch != null) "${shipMatch.groupValues[1]}円" else "詳細確認"
                            }
                        }

                        if (element.select(".Product__status--ended, .end").isNotEmpty()) {
                            continue
                        }

                        val item = ItemEntity(
                            itemId = itemId,
                            conditionId = condition.id.toLong(),
                            title = title,
                            url = itemUrl,
                            imageUrl = imageUrl,
                            localImagePath = null,
                            currentPrice = currentPrice,
                            promptDecisionPrice = promptPrice,
                            bidCount = bidCount,
                            shippingInfo = shippingInfo,
                            source = source,
                            description = null,
                            isNew = true,
                            isExcluded = false,
                            createdAt = currentTime
                        )

                        db.itemDao().insertItem(item)

                    } catch (e: Exception) {
                        LogManager.e("SearchWorker", "商品パースエラー", e)
                    }
                }
                LogManager.d("SearchWorker", "「${condition.patternName}」の検索・保存が完了しました")
            } catch (e: Exception) {
                LogManager.e("SearchWorker", "検索実行時エラー", e)
            }
        }
    }

    fun buildSearchUrl(condition: SearchConditionEntity): String {
        val query = condition.searchKeys.trim()
        val excludePart = if (condition.excludeKeys.isNotBlank()) {
            condition.excludeKeys.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .joinToString(" ") { key ->
                    if (key.startsWith("-")) key else "-$key"
                }
        } else {
            ""
        }
        val fullQuery = if (excludePart.isNotBlank()) "$query $excludePart" else query
        val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")
        return "https://auctions.yahoo.co.jp/search/search?p=$encodedQuery&exflg=1&b=1&n=30"
    }
}
