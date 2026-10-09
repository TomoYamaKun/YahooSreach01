//app/src/main/java/com/yahoosreach01/papa/PayPayFleaParser.kt
//ver 1.01-126
package com.yahoosreach01.papa

import com.yahoosreach01.papa.utils.LogManager
import org.jsoup.Jsoup

object PayPayFleaParser {

    fun parse(condition: SearchConditionEntity, encodedQuery: String, excludedIds: Set<String>, excludedUrls: Set<String>, currentTime: Long): List<ItemEntity> {
        val fetchedItems = mutableMapOf<String, ItemEntity>() // ID重複を防ぐためのマップ

        // ご提示いただいたYahoo!フリマの正確なソート別URL構造に対応
        val sortModes = listOf(
            "https://paypayfleamarket.yahoo.co.jp/search/$encodedQuery?page=1" to "おすすめ順",
            "https://paypayfleamarket.yahoo.co.jp/search/$encodedQuery?withSpeller=0&sort=price&order=asc" to "安価順",
            "https://paypayfleamarket.yahoo.co.jp/search/$encodedQuery?withSpeller=0&sort=likeCounts&order=desc" to "いいね多め順"
        )

        for ((fleaUrl, modeName) in sortModes) {
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
                    continue // エラー時は次のソートモードへ
                }

                val htmlContent = doc.html()
                LogManager.html("--- フリマ HTMLダンプ開始 [$modeName] (全文字数: ${htmlContent.length}) ---\n$htmlContent\n--- HTMLダンプ終了 ---")

                val fleaItems =
                    doc.select("a[href*='/item/'], div[data-reactid], li, div[class*='ItemCard']")

                LogManager.d(
                    "PayPayFleaParser",
                    "フリマ取得件数 ($modeName): ${fleaItems.size}"
                )

                for (element in fleaItems) {
                    try {
                        val aEl =
                            if (element.tagName() == "a")
                                element
                            else
                                element.select("a[href*='/item/']").first() ?: element.select("a").first() ?: continue

                        val rawHref = aEl.attr("href")
                        val itemUrl: String = if (rawHref != null) rawHref.toString() else ""

                        if (itemUrl.isBlank() || !itemUrl.contains("/item/")) continue

                        val titleElement = element.select("div[class*='title'], span[class*='title'], img, div, span").first()
                        val rawTitle = titleElement?.text()?.takeIf { !it.isNullOrBlank() } 
                            ?: element.select("img").attr("alt")
                            ?: aEl.text()

                        val title: String = if (rawTitle != null) rawTitle.toString().trim() else ""

                        if (title.isBlank() || title.length < 2 || title.matches(Regex("^[0-9]+$"))) continue

                        val fleaUrlWithoutQuery =
                            if (itemUrl.contains("?")) {
                                itemUrl.substring(0, itemUrl.indexOf("?"))
                            } else {
                                itemUrl
                            }

                        val fleaId =
                            if (fleaUrlWithoutQuery.contains("item/")) {
                                val pos = fleaUrlWithoutQuery.lastIndexOf("item/")
                                if (pos >= 0) {
                                    fleaUrlWithoutQuery.substring(pos + 5).trim('/')
                                } else {
                                    ""
                                }
                            } else {
                                ""
                            }

                        if (fleaId.isBlank()) continue

                        val finalItemId = "flea_$fleaId"
                        val finalUrl =
                            if (itemUrl.startsWith("http"))
                                itemUrl
                            else
                                "https://paypayfleamarket.yahoo.co.jp$itemUrl"

                        if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl)) {
                            continue
                        }

                        if (fetchedItems.containsKey(finalItemId)) continue

                        val imgEl = element.select("img").first()
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

                        val rawCardText = element.text()
                        val cardText: String = if (rawCardText != null) rawCardText.toString() else ""

                        val priceMatch =
                            Regex("([0-9,]+)円")
                                .find(cardText)

                        val price =
                            priceMatch
                                ?.groupValues
                                ?.get(1)
                                ?.replace(",", "")
                                ?.toIntOrNull()
                                ?: 0

                        if (price == 0) continue

                        if (condition.minPrice > 0 && price < condition.minPrice) continue
                        if (condition.maxPrice > 0 && price > condition.maxPrice) continue

                        val shippingInfo =
                            if (
                                cardText.contains("送料無料") ||
                                cardText.contains("送料込み")
                            )
                                "送料無料"
                            else
                                "送料確認"

                        val finalImgUrl =
                            if (imageUrl.startsWith("http"))
                                imageUrl
                            else if (imageUrl.startsWith("//"))
                                "https:$imageUrl"
                            else
                                imageUrl

                        fetchedItems[finalItemId] = ItemEntity(
                            itemId = finalItemId,
                            conditionId = condition.id.toLong(),
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
                            endTime = null,
                            createdAt = currentTime
                        )

                    } catch (e: Exception) {
                        LogManager.e("PayPayFleaParser", "フリマ個別パースエラー", e)
                    }
                }

            } catch (e: Exception) {
                LogManager.e("PayPayFleaParser", "フリマ検索エラー ($modeName)", e)
            }
        }

        return fetchedItems.values.toList()
    }
}
