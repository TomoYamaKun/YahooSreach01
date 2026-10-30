//app/src/main/java/com/yahoosreach01/papa/YahooAuctionParser.kt
//ver 1.02-45
package com.yahoosreach01.papa

import android.content.Context
import com.yahoosreach01.papa.utils.LogManager
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

object YahooAuctionParser {

    fun parse(context: Context, condition: SearchConditionEntity, encodedQuery: String, excludedIds: Set<String>, excludedUrls: Set<String>, currentTime: Long): List<ItemEntity> {
        val fetchedItems = mutableMapOf<String, ItemEntity>()

        val sortModes = listOf(
            "" to "おすすめ順",
            "select=31&mode=2" to "安い順",
            "select=6&mode=2" to "残り時間少ない",
            "select=22&mode=2" to "新着"
        )

        val baseQuery = condition.searchKeys.trim()
        val excludePart = if (condition.excludeKeys.isNotBlank()) {
            " " + condition.excludeKeys.trim().split("\\s+".toRegex()).joinToString(" ") { if (it.startsWith("-")) it else "-$it" }
        } else {
            ""
        }
        val fullQuery = baseQuery + excludePart
        val formattedQuery = URLEncoder.encode(fullQuery, "UTF-8")

        val categoryParam = if (condition.categories.isNotBlank()) {
            val catId = condition.categories.split(",")
                .map { it.trim() }
                .firstOrNull { it.matches(Regex("^[0-9]+$")) } ?: ""
            if (catId.isNotEmpty()) "&auccat=$catId" else ""
        } else {
            ""
        }

        for ((sortParam, modeName) in sortModes) {
            val pageOffsets = if (sortParam.contains("select=6") || sortParam.contains("select=22")) listOf(1, 51) else listOf(1, 101)

            for (bParam in pageOffsets) {
                try {
                    val pageNum = if (bParam <= 1) 1 else 2
                    LogManager.d("YahooAuctionParser", "「${condition.patternName}」: $modeName ($pageNum ページ目) 取得中...")

                    val priceMinParam = if (condition.minPrice > 0) "&aucminprice=${condition.minPrice}" else ""
                    val priceMaxParam = if (condition.maxPrice > 0) "&aucmaxprice=${condition.maxPrice}" else ""

                    val yahooUrl = if (sortParam.isNotEmpty()) {
                        "https://auctions.yahoo.co.jp/search/search?p=$formattedQuery$priceMinParam$priceMaxParam$categoryParam&fixed=0&b=$bParam&n=100&$sortParam"
                    } else {
                        "https://auctions.yahoo.co.jp/search/search?p=$formattedQuery$priceMinParam$priceMaxParam$categoryParam&fixed=0&b=$bParam&n=100"
                    }

                    LogManager.d("YahooAuctionParser", "ヤフオク検索URL ($modeName P$pageNum): $yahooUrl")

                    val response = try {
                        Jsoup.connect(yahooUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                            .timeout(15000)
                            .followRedirects(true)
                            .execute()
                    } catch (e: Exception) {
                        LogManager.e("YahooAuctionParser", "ヤフオク接続エラー ($modeName P$pageNum): ${e.message}")
                        continue
                    }

                    val doc = response.parse()
                    LogManager.exportHtmlDirectly(context, modeName, pageNum, doc.html())

                    var successCount = 0

                    // 1. JSON (pageData) からの基本データ抽出
                    val scriptEls = doc.select("script")
                    for (script in scriptEls) {
                        val scriptText = script.html()
                        if (scriptText.contains("var pageData =")) {
                            try {
                                val jsonStartIndex = scriptText.indexOf("var pageData =") + "var pageData =".length
                                val jsonEndIndex = scriptText.indexOf("};", jsonStartIndex) + 1
                                if (jsonStartIndex in 0 until jsonEndIndex) {
                                    val jsonString = scriptText.substring(jsonStartIndex, jsonEndIndex).trim()
                                    val rootObj = JSONObject(jsonString)
                                    val itemsArray = rootObj.optJSONArray("items")

                                    if (itemsArray != null) {
                                        for (i in 0 until itemsArray.length()) {
                                            val itemObj = itemsArray.getJSONObject(i)
                                            val auctionId = itemObj.optString("productID")
                                            val title = itemObj.optString("productName")
                                            val currentPrice = itemObj.optInt("price", 0)
                                            val promptPrice = itemObj.optInt("winPrice", 0)
                                            val jsonBidCount = itemObj.optInt("bids", 0)
                                            val jsonEndTime = itemObj.optString("endtime", "-")

                                            if (auctionId.isBlank() || title.isBlank()) continue

                                            val finalItemId = "auc_$auctionId"
                                            val finalUrl = "https://auctions.yahoo.co.jp/jp/auction/$auctionId"

                                            if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl) || fetchedItems.containsKey(finalItemId)) {
                                                continue
                                            }

                                            val checkPrice = if (currentPrice > 0) currentPrice else promptPrice
                                            if (condition.minPrice > 0 && checkPrice < condition.minPrice) continue
                                            if (condition.maxPrice > 0 && checkPrice > condition.maxPrice) continue

                                            fetchedItems[finalItemId] = ItemEntity(
                                                itemId = finalItemId,
                                                conditionId = condition.id.toLong(),
                                                title = title.take(80),
                                                url = finalUrl,
                                                imageUrl = "",
                                                localImagePath = null,
                                                currentPrice = currentPrice,
                                                promptDecisionPrice = promptPrice,
                                                bidCount = jsonBidCount, // JSONの値を一旦セット (DOM側で上書き・補完)
                                                shippingInfo = "詳細確認",
                                                source = "auction",
                                                description = null,
                                                isNew = true,
                                                isExcluded = false,
                                                isFavorite = false,
                                                endTime = jsonEndTime, // JSONの値を一旦セット (DOM側で上書き・補完)
                                                createdAt = currentTime
                                            )
                                            successCount++
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                LogManager.e("YahooAuctionParser", "pageData JSONパース例外", e)
                            }
                        }
                    }

                    // 2. DOM (li要素) から「画像」「正確な入札件数」「正確な終了時間」「送料」を1件ずつ完璧に抽出し補完・上書き
                    val itemRows = doc.select("ul.bd-t_none > li, ul > li:has(a[href*='/auction/'])")
                    for (li in itemRows) {
                        try {
                            val linkEl = li.select("a[href*='/auction/'], a[href*='jp/auction/']").first() ?: continue
                            val href = linkEl.attr("href") ?: ""
                            val auctionId = href.substringAfter("auction/").trim('/')
                            val itemIdKey = "auc_$auctionId"

                            val targetItem = fetchedItems[itemIdKey] ?: continue

                            // 画像URL
                            val imgEl = li.select("img").first()
                            val rawImg = imgEl?.attr("src") ?: ""
                            val imageUrl = if (rawImg.startsWith("http")) rawImg else if (rawImg.startsWith("//")) "https:$rawImg" else rawImg

                            val cardText = li.text() ?: ""

                            // 入札件数の補完 (SVGアイコン周辺またはテキストから確実抽出)
                            var bidCount = targetItem.bidCount
                            if (bidCount == 0) {
                                val bidSvg = li.select("svg[aria-label='入札件数']").first()
                                if (bidSvg != null) {
                                    val parentText = bidSvg.parent()?.text() ?: ""
                                    bidCount = Regex("([0-9,]+)").find(parentText)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
                                }
                                if (bidCount == 0) {
                                    val bidMatch = Regex("入札[:\\s]*([0-9,]+)|([0-9,]+)件").find(cardText)
                                    if (bidMatch != null) {
                                        val group = bidMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "0"
                                        bidCount = group.replace(",", "").toIntOrNull() ?: 0
                                    }
                                }
                            }

                            // 終了時間の補完 (1件目やJSONで取れなかったもの対策)
                            var endTime = targetItem.endTime
                            if (endTime.isNullOrBlank() || endTime == "-") {
                                val timeMatch = Regex("([0-9]+/[0-9]+\\s+[0-9]+:[0-9]+)").find(cardText)
                                if (timeMatch != null) {
                                    endTime = timeMatch.groupValues[1].trim()
                                } else {
                                    val oldTimeMatch = Regex("([0-9]+月[0-9]+日[^0-9]*[0-9]+時[0-9]+分|本日終了|残り[^\\s]+|あと[^\\s]+)").find(cardText)
                                    if (oldTimeMatch != null) {
                                        endTime = oldTimeMatch.groupValues[1].trim()
                                    }
                                }
                            }

                            // 送料
                            val shippingInfo = when {
                                cardText.contains("送料無料") || cardText.contains("送料込") -> "送料無料"
                                cardText.contains("着払い") -> "着払い"
                                cardText.contains("＋送料") -> {
                                    val shipMatch = Regex("＋送料([0-9,]+)円").find(cardText)
                                    if (shipMatch != null) "${shipMatch.groupValues[1]}円" else "詳細確認"
                                }
                                else -> {
                                    val shipMatch = Regex("送料[:\\s]*([0-9,]+)円").find(cardText)
                                    if (shipMatch != null) "${shipMatch.groupValues[1]}円" else "詳細確認"
                                }
                            }

                            fetchedItems[itemIdKey] = targetItem.copy(
                                imageUrl = if (imageUrl.isNotBlank()) imageUrl else targetItem.imageUrl,
                                bidCount = bidCount,
                                endTime = if (!endTime.isNullOrBlank()) endTime else "-",
                                shippingInfo = shippingInfo
                            )
                        } catch (e: Exception) {
                            // スキップ
                        }
                    }

                    LogManager.d("YahooAuctionParser", "ヤフオクパース詳細 ($modeName P$pageNum): 成功=$successCount")

                } catch (e: Exception) {
                    LogManager.e("YahooAuctionParser", "ヤフオク検索エラー ($modeName)", e)
                }
            }
        }

        LogManager.d("YahooAuctionParser", "ヤフオク全ソート合計取得完了: 有効アイテム ${fetchedItems.size}件")
        return fetchedItems.values.toList()
    }
}
