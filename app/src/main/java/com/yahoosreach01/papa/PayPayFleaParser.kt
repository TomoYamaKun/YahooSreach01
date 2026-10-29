//app/src/main/java/com/yahoosreach01/papa/PayPayFleaParser.kt
//ver 1.02-06
package com.yahoosreach01.papa

import com.yahoosreach01.papa.utils.LogManager
import org.jsoup.Jsoup

object PayPayFleaParser {

    fun parse(condition: SearchConditionEntity, encodedQuery: String, excludedIds: Set<String>, excludedUrls: Set<String>, currentTime: Long): List<ItemEntity> {
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

                val fleaItems = doc.select("a[href*='/item/'], div[data-reactid], li, div[class*='ItemCard']")

                for (element in fleaItems) {
                    try {
                        val aEl = if (element.tagName() == "a") {
                            element
                        } else {
                            element.select("a[href*='/item/']").first() ?: element.select("a").first() 
                        }

                        if (aEl == null) continue

                        val rawHref = aEl.attr("href")
                        val itemUrl: String = if (rawHref != null) rawHref.toString() else ""

                        if (itemUrl.isBlank() || !itemUrl.contains("/item/")) continue

                        val rawCardText = element.text() ?: ""

                        val lowerCardText = rawCardText.lowercase()
                        if (lowerCardText.contains("sold") || 
                            lowerCardText.contains("売り切れ") || 
                            lowerCardText.contains("販売終了") || 
                            element.select(".is-sold, [class*='sold'], [class*='Sold']").isNotEmpty()) {
                            continue
                        }

                        var rawTitle = ""
                        val imgEl = element.select("img").first()
                        if (imgEl != null) {
                            val altText = imgEl.attr("alt")
                            if (!altText.isNullOrBlank() && altText.length > 3 && !altText.contains("円") && !altText.contains("いいね")) {
                                rawTitle = altText
                            }
                        }

                        if (rawTitle.isBlank()) {
                            val ariaLabel = aEl.attr("aria-label")
                            if (!ariaLabel.isNullOrBlank() && !ariaLabel.contains("いいね")) {
                                rawTitle = ariaLabel
                            }
                        }

                        if (rawTitle.isBlank()) {
                            val titleSpan = element.select("div[class*='title'], span[class*='title'], p[class*='title']").first()
                            if (titleSpan != null) {
                                rawTitle = titleSpan.text()
                            }
                        }

                        if (rawTitle.isBlank()) {
                            val candidateEls = element.select("span, div, p")
                            for (el in candidateEls) {
                                val txt = el.text().trim()
                                if (txt.length >= 6 && !txt.contains("いいね") && !txt.contains("円") && !txt.matches(Regex("^[0-9,]+$")) && !txt.contains("送料") && !txt.contains("価格")) {
                                    rawTitle = txt
                                    break
                                }
                            }
                        }

                        val title: String = rawTitle.trim()
                        if (title.isBlank() || title.length < 2 || title.matches(Regex("^[0-9]+$")) || title.contains("いいね")) continue

                        val fleaUrlWithoutQuery = if (itemUrl.contains("?")) {
                            itemUrl.substring(0, itemUrl.indexOf("?"))
                        } else {
                            itemUrl
                        }

                        val fleaId = if (fleaUrlWithoutQuery.contains("item/")) {
                            val pos = fleaUrlWithoutQuery.lastIndexOf("item/")
                            if (pos >= 0) fleaUrlWithoutQuery.substring(pos + 5).trim('/') else ""
                        } else {
                            ""
                        }

                        if (fleaId.isBlank()) continue

                        val finalItemId = "flea_$fleaId"
                        val finalUrl = if (itemUrl.startsWith("http")) itemUrl else "https://paypayfleamarket.yahoo.co.jp$itemUrl"

                        if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl)) continue
                        if (fetchedItems.containsKey(finalItemId)) continue

                        var rawImg = imgEl?.attr("data-src")?.takeIf { !it.isNullOrEmpty() }
                            ?: imgEl?.attr("data-original")?.takeIf { !it.isNullOrEmpty() }
                            ?: imgEl?.attr("src")?.takeIf { !it.isNullOrEmpty() }
                            ?: imgEl?.attr("srcset")?.takeIf { !it.isNullOrEmpty() }
                            ?: ""

                        if (rawImg.isBlank()) {
                            val styleAttr = element.select("[style*='background-image']").attr("style")
                            val bgMatch = Regex("url\\(['\"]?(.*?)['\"]?\\)").find(styleAttr)
                            if (bgMatch != null) {
                                rawImg = bgMatch.groupValues[1]
                            }
                        }

                        val imageUrl: String = if (rawImg.startsWith("http")) rawImg else if (rawImg.startsWith("//")) "https:$rawImg" else rawImg
                        if (imageUrl.isBlank()) continue

                        val priceMatches = Regex("([0-9,]+)円").findAll(rawCardText).toList()
                        val extractedPrices = priceMatches.map { it.groupValues[1].replace(",", "").toIntOrNull() ?: 0 }.filter { it > 0 }
                        val price = extractedPrices.firstOrNull() ?: 0

                        if (price == 0) continue
                        if (condition.minPrice > 0 && price < condition.minPrice) continue
                        if (condition.maxPrice > 0 && price > condition.maxPrice) continue

                        val shippingInfo = if (rawCardText.contains("送料無料") || rawCardText.contains("送料込み")) {
                            "送料無料"
                        } else {
                            "送料確認"
                        }

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
                            shippingInfo = shippingInfo,
                            source = "fleamarket",
                            description = null,
                            isNew = true,
                            isExcluded = false,
                            isFavorite = false,
                            endTime = null,
                            createdAt = currentTime
                        )

                    } catch (e: Exception) {
                        LogManager.e("PayPayFleaParser", "フリマ個別パースエラー ($modeName)", e)
                    }
                }

            } catch (e: Exception) {
                LogManager.e("PayPayFleaParser", "フリマ検索エラー ($modeName)", e)
            }
        }

        LogManager.d("PayPayFleaParser", "フリマ全ソート合計取得完了: 成功 ${fetchedItems.size}件")
        return fetchedItems.values.toList()
    }
}
