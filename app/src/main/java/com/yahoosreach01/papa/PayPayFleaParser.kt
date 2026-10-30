//app/src/main/java/com/yahoosreach01/papa/PayPayFleaParser.kt
//ver 1.02-43
package com.yahoosreach01.papa

import android.content.Context
import com.yahoosreach01.papa.utils.LogManager
import org.json.JSONObject
import org.jsoup.Jsoup

object PayPayFleaParser {

    fun parse(context: Context, condition: SearchConditionEntity, encodedQuery: String, excludedIds: Set<String>, excludedUrls: Set<String>, currentTime: Long): List<ItemEntity> {
        val fetchedItems = mutableMapOf<String, ItemEntity>()

        val rawQuery = (condition.searchKeys.trim() + if (condition.excludeKeys.isNotBlank()) {
            " " + condition.excludeKeys.trim().split("\\s+".toRegex()).joinToString(" ") { if (it.startsWith("-")) it else "-$it" }
        } else {
            ""
        }).trim()
        
        val formattedQuery = rawQuery.replace(" ", "%20")

        val categoryParam = if (condition.categories.isNotBlank()) {
            val fleaPath = condition.categories.split(",")
                .map { it.trim() }
                .firstOrNull { it.contains("/") } ?: ""
            
            if (fleaPath.isNotBlank()) {
                val encodedCatIds = fleaPath.replace("/", "%2C")
                "&categoryIds=$encodedCatIds"
            } else {
                ""
            }
        } else {
            ""
        }

        val priceMinParam = if (condition.minPrice > 0) "&minPrice=${condition.minPrice}" else ""
        val priceMaxParam = if (condition.maxPrice > 0) "&maxPrice=${condition.maxPrice}" else ""

        val sortModes = listOf(
            "&withSpeller=0&sort=price&order=asc" to "価格が安い順",
            "&withSpeller=0&sort=openTime&order=desc" to "新着順",
            "&withSpeller=0&sort=likeCounts&order=desc" to "いいね順"
        )

        for ((sortParam, modeName) in sortModes) {
            val fleaUrl = "https://paypayfleamarket.yahoo.co.jp/search/$formattedQuery?open=1$categoryParam$priceMinParam$priceMaxParam$sortParam"

            try {
                LogManager.d(
                    "PayPayFleaParser",
                    "「${condition.patternName}」フリマ検索URL ($modeName): $fleaUrl"
                )

                val doc = try {
                    Jsoup.connect(fleaUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get()
                } catch (e: Exception) {
                    LogManager.e("PayPayFleaParser", "フリマ接続・取得エラー ($modeName): ${e.message}")
                    continue
                }

                // 生HTMLをログに出力
                LogManager.exportHtmlDirectly(context, "Flea_$modeName", 1, doc.html())

                var successCount = 0

                // ★ <script id="__NEXT_DATA__"> 内のJSONから商品リストを直接安全に取得する
                val nextDataScript = doc.select("script#__NEXT_DATA__").first()
                if (nextDataScript != null) {
                    try {
                        val jsonRoot = JSONObject(nextDataScript.html())
                        val props = jsonRoot.optJSONObject("props")
                        val pageProps = props?.optJSONObject("pageProps")
                        val initialState = pageProps?.optJSONObject("initialState")
                        val searchState = initialState?.optJSONObject("searchState")
                        val resultObj = searchState?.optJSONObject("result")
                        val itemsArray = resultObj?.optJSONArray("items")

                        if (itemsArray != null) {
                            for (i in 0 until itemsArray.length()) {
                                val itemObj = itemsArray.getJSONObject(i)
                                val itemIdStr = itemObj.optString("id") // 例: "z702370812"
                                val title = itemObj.optString("title")
                                val price = itemObj.optInt("price", 0)
                                val thumbUrl = itemObj.optString("thumbnailImageUrl")

                                if (itemIdStr.isBlank() || title.isBlank() || price == 0) continue

                                val finalItemId = "flea_$itemIdStr"
                                val finalUrl = "https://paypayfleamarket.yahoo.co.jp/item/$itemIdStr"

                                if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl)) continue
                                if (fetchedItems.containsKey(finalItemId)) continue

                                // 価格フィルターの適用
                                if (condition.minPrice > 0 && price < condition.minPrice) continue
                                if (condition.maxPrice > 0 && price > condition.maxPrice) continue

                                // フリマは基本的に全品送料無料（説明文に準じる）
                                val shippingInfo = "送料無料"

                                fetchedItems[finalItemId] = ItemEntity(
                                    itemId = finalItemId,
                                    conditionId = condition.id.toLong(),
                                    title = title.take(80),
                                    url = finalUrl,
                                    imageUrl = thumbUrl,
                                    localImagePath = null,
                                    currentPrice = 0,
                                    promptDecisionPrice = price,
                                    bidCount = 0,
                                    shippingInfo = shippingInfo,
                                    source = "fleamarket",
                                    description = null,
                                    isNew = true,
                                    isExcluded = false,
                                    isFavorite = false,
                                    endTime = null,
                                    createdAt = currentTime
                                )
                                successCount++
                            }
                        }
                    } catch (jsonEx: Exception) {
                        LogManager.e("PayPayFleaParser", "JSONパースエラー ($modeName)", jsonEx)
                    }
                }

                // 万が一JSONが見つからない場合のフォールバック（従来のDOMセレクト）
                if (successCount == 0) {
                    val fleaItems = doc.select("a[href*='/item/']")
                    for (aEl in fleaItems) {
                        try {
                            val itemUrl = aEl.attr("href") ?: ""
                            if (itemUrl.isBlank() || !itemUrl.contains("/item/")) continue

                            val rawCardText = aEl.text() ?: ""
                            val title = aEl.attr("aria-label").takeIf { !it.isNullOrBlank() } ?: rawCardText
                            if (title.isBlank()) continue

                            val fleaId = itemUrl.substringAfter("item/").trim('/')
                            if (fleaId.isBlank()) continue

                            val finalItemId = "flea_$fleaId"
                            val finalUrl = if (itemUrl.startsWith("http")) itemUrl else "https://paypayfleamarket.yahoo.co.jp$itemUrl"

                            if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl) || fetchedItems.containsKey(finalItemId)) continue

                            val imgEl = aEl.select("img").first()
                            val imageUrl = imgEl?.attr("src") ?: ""
                            if (imageUrl.isBlank()) continue

                            val priceMatch = Regex("([0-9,]+)円").find(rawCardText)
                            val price = priceMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
                            if (price == 0) continue

                            if (condition.minPrice > 0 && price < condition.minPrice) continue
                            if (condition.maxPrice > 0 && price > condition.maxPrice) continue

                            fetchedItems[finalItemId] = ItemEntity(
                                itemId = finalItemId,
                                conditionId = condition.id.toLong(),
                                title = title.take(80),
                                url = finalUrl,
                                imageUrl = imageUrl,
                                localImagePath = null,
                                currentPrice = 0,
                                promptDecisionPrice = price,
                                bidCount = 0,
                                shippingInfo = "送料無料",
                                source = "fleamarket",
                                description = null,
                                isNew = true,
                                isExcluded = false,
                                isFavorite = false,
                                endTime = null,
                                createdAt = currentTime
                            )
                        } catch (e: Exception) {
                            // スキップ
                        }
                    }
                }

                LogManager.d("PayPayFleaParser", "フリマパース詳細 ($modeName): 成功=$successCount 件")

            } catch (e: Exception) {
                LogManager.e("PayPayFleaParser", "フリマ検索エラー ($modeName)", e)
            }
        }

        LogManager.d("PayPayFleaParser", "フリマ全ソート合計取得完了: 有効アイテム ${fetchedItems.size}件")
        return fetchedItems.values.toList()
    }
}
