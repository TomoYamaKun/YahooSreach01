//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-19
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

                val items = doc.select(".Product")
                LogManager.d("SearchWorker", "取得件数: ${items.size}")

                val db = AppDatabase.getDatabase(context)
                val currentTime = System.currentTimeMillis()

                for (element in items) {
                    try {
                        val titleEl = element.select(".Product__titleLink")
                        val title = titleEl.text() ?: continue
                        val itemUrl = titleEl.attr("href")
                        
                        val itemId = itemUrl.substringAfter("article/").substringBefore("?")
                        if (itemId.isBlank()) continue

                        val imgEl = element.select(".Product__image img")
                        val imageUrl = imgEl.attr("data-src").ifEmpty { imgEl.attr("src") }

                        // 現在値の抽出
                        val priceEl = element.select(".Product__priceValue")
                        val currentPrice = priceEl.text().replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

                        // 即決値の抽出（もしあれば）
                        val promptEl = element.select(".Product__bidInfo")
                        val promptText = promptEl.text()
                        val promptPrice = if (promptText.contains("即決")) {
                            promptText.substringAfter("即決").replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                        } else {
                            0
                        }

                        if (element.select(".Product__status--ended").isNotEmpty()) {
                            continue
                        }

                        // 詳細ページを軽くスクレイピングして説明文を取得（アプリ内完結のため）
                        var description: String? = null
                        try {
                            val detailDoc = Jsoup.connect(itemUrl).userAgent("Mozilla/5.0").timeout(5000).get()
                            description = detailDoc.select(".ProductExplanation__body, .g-section").text()
                        } catch (e: Exception) {
                            description = "詳細文の取得に失敗しました"
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
                            source = "auction",
                            description = description,
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
