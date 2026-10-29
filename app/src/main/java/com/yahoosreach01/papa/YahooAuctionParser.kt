//app/src/main/java/com/yahoosreach01/papa/YahooAuctionParser.kt
//ver 1.02-30
package com.yahoosreach01.papa

import com.yahoosreach01.papa.utils.LogManager
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URLEncoder

object YahooAuctionParser {

    fun parse(condition: SearchConditionEntity, encodedQuery: String, excludedIds: Set<String>, excludedUrls: Set<String>, currentTime: Long): List<ItemEntity> {
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

                    LogManager.d(
                        "YahooAuctionParser",
                        "ヤフオク検索URL ($modeName P$pageNum): $yahooUrl"
                    )

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

                    LogManager.html("URL: $yahooUrl\nSTATUS: ${response.statusCode()}\nHTML LENGTH: ${doc.html().length}\n\n${doc.html().take(4000)}")

                    val itemLinks = doc.select("a[href*='/auction/'], a[href*='jp/auction/']")
                    LogManager.d("YahooAuctionParser", "ヤフオク候補リンク件数 ($modeName P$pageNum): ${itemLinks.size}")

                    var successCount = 0
                    var skipInvalid = 0

                    for (linkEl in itemLinks) {
                        try {
                            val itemUrl = linkEl.attr("href") ?: ""
                            if (itemUrl.isBlank() || !itemUrl.contains("auction")) {
                                skipInvalid++
                                continue
                            }

                            val containerEl = linkEl.parents().firstOrNull { 
                                it.className().contains("Product") || it.className().contains("item") || it.className().contains("Card") || it.tagName() == "li" || it.tagName() == "div" 
                            } ?: linkEl

                            val rawTitle = linkEl.text().ifBlank { containerEl.text() }
                            val title = rawTitle.trim()

                            if (title.isBlank() || title.length < 3 || title.matches(Regex("^[0-9]+$")) || title == "送料無料" || title == "New!!") {
                                skipInvalid++
                                continue
                            }

                            val cleanUrl = if (itemUrl.contains("?")) itemUrl.substring(0, itemUrl.indexOf("?")) else itemUrl
                            val pos = cleanUrl.lastIndexOf("auction/")
                            val auctionId = if (pos >= 0) {
                                cleanUrl.substring(pos + 8).trim('/')
                            } else {
                                cleanUrl.hashCode().toString()
                            }

                            val finalItemId = "auc_$auctionId"
                            val finalUrl = if (itemUrl.startsWith("http")) itemUrl else "https://auctions.yahoo.co.jp$itemUrl"

                            if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl) || fetchedItems.containsKey(finalItemId)) {
                                skipInvalid++
                                continue
                            }

                            // ★ 画像取得の強化（複数の属性や親要素からのimg探索）
                            val imgEl = containerEl.select("img").first() 
                                ?: linkEl.select("img").first() 
                                ?: linkEl.parents().select("img").first()

                            val rawImg = imgEl?.attr("data-src") 
                                ?: imgEl?.attr("src") 
                                ?: imgEl?.attr("data-original") 
                                ?: imgEl?.attr("srcset")?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull() 
                                ?: ""
                            val imageUrl = if (rawImg.startsWith("http")) rawImg else if (rawImg.startsWith("//")) "https:$rawImg" else rawImg

                            val cardText = containerEl.text() ?: ""
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
                                val priceMatch = Regex("([0-9,]+)円").find(cardText)
                                if (priceMatch != null) {
                                    currentPrice = priceMatch.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                                }
                            }

                            if (currentPrice == 0 && promptPrice == 0) {
                                skipInvalid++
                                continue
                            }

                            // ★ 入札件数の抽出強化
                            val bidMatch = Regex("入札[:\\s]*([0-9,]+)|([0-9,]+)件|入札数[:\\s]*([0-9,]+)").find(cardText)
                            val bidCount = if (bidMatch != null) {
                                val group = bidMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "0"
                                group.replace(",", "").toIntOrNull() ?: 0
                            } else 0

                            // ★ 終了時間の抽出強化（「残り」「あと」「日」「時間」「分」「終了」に加え、日付形式に対応）
                            var endTime = "-"
                            val timeMatch = Regex("([0-9]+月[0-9]+日[^0-9]*[0-9]+時[0-9]+分|本日終了|残り[^\\s]+|あと[^\\s]+|[0-9]+日[^\\s]*終了|[0-9]+時間[^\\s]*終了)").find(cardText)
                            if (timeMatch != null) {
                                endTime = timeMatch.groupValues[1].trim()
                            } else {
                                val words = cardText.split("\\s+".toRegex())
                                for (w in words) {
                                    if ((w.contains("残り") || w.contains("あと") || w.contains("日") || w.contains("時間")) && w.length < 15 && !w.contains("円")) {
                                        endTime = w
                                        break
                                    }
                                }
                            }

                            val shippingInfo = when {
                                cardText.contains("送料無料") || cardText.contains("送料込") -> "送料無料"
                                cardText.contains("着払い") -> "着払い"
                                else -> {
                                    val shipMatch = Regex("送料[:\\s]*([0-9,]+)円").find(cardText)
                                    if (shipMatch != null) "${shipMatch.groupValues[1]}円" else "詳細確認"
                                }
                            }

                            fetchedItems[finalItemId] = ItemEntity(
                                itemId = finalItemId,
                                conditionId = condition.id.toLong(),
                                title = title.take(80),
                                url = finalUrl,
                                imageUrl = imageUrl,
                                localImagePath = null,
                                currentPrice = currentPrice,
                                promptDecisionPrice = promptPrice,
                                bidCount = bidCount,
                                shippingInfo = shippingInfo,
                                source = "auction",
                                description = null,
                                isNew = true,
                                isExcluded = false,
                                isFavorite = false,
                                endTime = endTime,
                                createdAt = currentTime
                            )
                            successCount++

                        } catch (e: Exception) {
                            // 個別パースエラーはスキップ
                        }
                    }

                    LogManager.d("YahooAuctionParser", "ヤフオクパース詳細 ($modeName P$pageNum): 成功=$successCount, スキップ=$skipInvalid")

                } catch (e: Exception) {
                    LogManager.e("YahooAuctionParser", "ヤフオク検索エラー ($modeName)", e)
                }
            }
        }

        LogManager.d("YahooAuctionParser", "ヤフオク全ソート合計取得完了: 有効アイテム ${fetchedItems.size}件")
        return fetchedItems.values.toList()
    }
}
