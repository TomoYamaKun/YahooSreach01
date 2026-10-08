//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-51
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
            val db = AppDatabase.getDatabase(context)
            val conditionId = condition.id.toLong()

            db.itemDao().clearActiveItemsForCondition(conditionId)

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
            val currentTime = System.currentTimeMillis()

            // 1. ヤフオク検索
            if (condition.targetService != "fleamarket") {
                try {
                    val sortParam = when (condition.sortOrder) {
                        "s" -> "&s1=cbcl&o=a"
                        "b" -> "&s1=bid&o=d"
                        "e" -> "&s1=end&o=a"
                        else -> "&s1=new&o=d"
                    }
                    val priceMinParam = if (condition.minPrice > 0) "&aucminprice=${condition.minPrice}" else ""
                    val priceMaxParam = if (condition.maxPrice > 0) "&aucmaxprice=${condition.maxPrice}" else ""
                    val categoryParam = if (condition.categories.isNotBlank()) "&category=${condition.categories.trim()}" else ""

                    val yahooUrl = "https://auctions.yahoo.co.jp/search/search?p=$encodedQuery$sortParam$priceMinParam$priceMaxParam$categoryParam&exflg=1&b=1&n=50"
                    LogManager.d("SearchWorker", "ヤフオク検索URL: $yahooUrl")

                    val doc = Jsoup.connect(yahooUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get()

                    val items = doc.select(".Product, li.clst, div.Product")
                    LogManager.d("SearchWorker", "ヤフオク取得件数: ${items.size}")

                    for (element in items) {
                        try {
                            val titleEl = element.select(".Product__titleLink, a.thm, a[href*='auction']")
                            val title: String = titleEl.text() ?: continue
                            if (title.isBlank()) continue
                            
                            val rawHref = titleEl.attr("href")
                            val itemUrl: String = (rawHref ?: "").toString()
                            if (itemUrl.isBlank()) continue

                            // メソッドチェーンを完全に分離し、一時変数で型を保証
                            val urlWithoutQuery: String = itemUrl.substringBefore("?", itemUrl)
                            val itemId: String = if (urlWithoutQuery.contains("auction/")) {
                                urlWithoutQuery.substringAfterLast("auction/")
                            } else {
                                urlWithoutQuery.hashCode().toString()
                            }
                            val finalItemId = if (itemId.isBlank()) itemUrl.hashCode().toString() else itemId

                            val imgEl = element.select("img")
                            val rawImg = imgEl.attr("data-src").takeIf { !it.isNullOrEmpty() } ?: imgEl.attr("src")
                            val imageUrl: String = (rawImg ?: "").toString()
                            val cardText: String = (element.text() ?: "").toString()

                            var currentPrice = 0
                            var promptPrice = 0

                            val currentMatch = Regex("現在[:\\s]*([0-9,]+)円").find(cardText)
                            if (currentMatch != null) {
                                currentPrice = currentMatch.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                            }

                            val promptMatch = Regex("即決[:\\s]*([0-9,]+)円").find(cardText)
                            if (promptMatch != null) {
                                promptPrice = promptMatch.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                            }

                            if (currentPrice == 0 && promptPrice == 0) {
                                val priceText: String = (element.select(".Product__priceValue, .prc").text() ?: "").toString()
                                val rawMatch = Regex("([0-9,]+)").find(priceText)
                                currentPrice = rawMatch?.groupValues?.get(1)?.replace(",", "").toIntOrNull() ?: 0
                            }

                            var bidCount = 0
                            val bidMatch = Regex("(?:入札[:\\s]*)?([0-9]+)件").find(cardText)
                            if (bidMatch != null) {
                                bidCount = bidMatch.groupValues[1].toIntOrNull() ?: 0
                            }

                            val shippingInfo = when {
                                cardText.contains("送料無料") || cardText.contains("送料込") -> "送料無料"
                                cardText.contains("着払い") -> "着払い"
                                else -> {
                                    val shipMatch = Regex("送料[:\\s]*([0-9,]+)円").find(cardText)
                                    if (shipMatch != null) "${shipMatch.groupValues[1]}円" else "詳細確認"
                                }
                            }

                            if (element.select(".Product__status--ended, .end").isNotEmpty()) continue

                            val finalUrl = if (itemUrl.startsWith("http")) itemUrl else "https://auctions.yahoo.co.jp$itemUrl"
                            val finalImgUrl = if (imageUrl.startsWith("http")) imageUrl else "https:$imageUrl"

                            val item = ItemEntity(
                                itemId = finalItemId,
                                conditionId = conditionId,
                                title = title,
                                url = finalUrl,
                                imageUrl = finalImgUrl,
                                localImagePath = null,
                                currentPrice = currentPrice,
                                promptDecisionPrice = promptPrice,
                                bidCount = bidCount,
                                shippingInfo = shippingInfo,
                                source = "auction",
                                description = null,
                                isNew = true,
                                isExcluded = false,
                                createdAt = currentTime
                            )
                            db.itemDao().insertItem(item)
                        } catch (e: Exception) {
                            LogManager.e("SearchWorker", "ヤフオクパースエラー", e)
                        }
                    }
                } catch (e: Exception) {
                    LogManager.e("SearchWorker", "ヤフオク検索エラー", e)
                }
            }

            // 2. フリマ検索
            if (condition.targetService != "auction") {
                try {
                    val fleaUrl = "https://paypayfleamarket.yahoo.co.jp/search/$encodedQuery"
                    LogManager.d("SearchWorker", "フリマ検索URL: $fleaUrl")

                    val doc = Jsoup.connect(fleaUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get()

                    val fleaItems = doc.select("a[href*='/item/'], div[data-reactid], li")
                    for (element in fleaItems) {
                        try {
                            val aEl = if (element.tagName() == "a") element else element.select("a").first()
                            val itemUrl: String = (aEl?.attr("href") ?: "").toString()
                            if (itemUrl.isBlank() || !itemUrl.contains("/item/")) continue

                            val title: String = (element.select("div, span").text() ?: "").toString()
                            if (title.isBlank() || title.length < 3) continue

                            // メソッドチェーンを完全に分離し、一時変数で型を保証
                            val fleaUrlWithoutQuery: String = itemUrl.substringBefore("?", itemUrl)
                            val fleaId: String = if (fleaUrlWithoutQuery.contains("item/")) {
                                fleaUrlWithoutQuery.substringAfterLast("item/")
                            } else {
                                ""
                            }
                            if (fleaId.isBlank()) continue

                            val imgEl = element.select("img")
                            val rawImg = imgEl.attr("data-src").takeIf { !it.isNullOrEmpty() } ?: imgEl.attr("src")
                            val imageUrl: String = (rawImg ?: "").toString()
                            if (imageUrl.isBlank()) continue

                            val cardText: String = (element.text() ?: "").toString()
                            val priceMatch = Regex("([0-9,]+)円").find(cardText)
                            val price = priceMatch?.groupValues?.get(1)?.replace(",", "").toIntOrNull() ?: 0
                            if (price == 0) continue

                            val shippingInfo = if (cardText.contains("送料無料") || cardText.contains("送料込み")) "送料無料" else "送料確認"

                            val finalUrl = if (itemUrl.startsWith("http")) itemUrl else "https://paypayfleamarket.yahoo.co.jp$itemUrl"
                            val finalImgUrl = if (imageUrl.startsWith("http")) imageUrl else "https:$imageUrl"

                            val item = ItemEntity(
                                itemId = "flea_$fleaId",
                                conditionId = conditionId,
                                title = title.take(80),
                                url = finalUrl,
                                imageUrl = finalImgUrl,
                                localImagePath = null,
                                currentPrice = 0,
                                promptDecisionPrice = price,
                                bidCount = 0,
                                shippingInfo = shippingInfo,
                                source = "fleamarket",
                                description = null,
                                isNew = true,
                                isExcluded = false,
                                createdAt = currentTime
                            )
                            db.itemDao().insertItem(item)
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {
                    LogManager.e("SearchWorker", "フリマ検索エラー", e)
                }
            }
            LogManager.d("SearchWorker", "「${condition.patternName}」の全検索・保存が完了しました")
        }
    }

    fun buildSearchUrl(condition: SearchConditionEntity): String {
        return ""
    }
}
