//app/src/main/java/com/yahoosreach01/papa/YahooAuctionParser.kt
//ver 1.01-132
package com.yahoosreach01.papa

import com.yahoosreach01.papa.utils.LogManager
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object YahooAuctionParser {

    fun parse(condition: SearchConditionEntity, encodedQuery: String, excludedIds: Set<String>, excludedUrls: Set<String>, currentTime: Long): List<ItemEntity> {
        val fetchedItems = mutableMapOf<String, ItemEntity>() // ID重複を防ぐためのマップ

        // おすすめ、最安値順、終了時間間近、入札件数多いの4つのソートモード
        val sortModes = listOf(
            "" to "おすすめ順",
            "s2" to "最安値順",
            "s3" to "終了時間間近",
            "s4" to "入札件数多い"
        )

        for ((sortParam, modeName) in sortModes) {
            // ★ 1ページ目と2ページ目のみを取得するループ (page 1 = b=1, page 2 = b=51 または b=101)
            val pageOffsets = if (sortParam == "s3") listOf(1, 51) else listOf(1, 101)

            for (bParam in pageOffsets) {
                try {
                    val pageNum = if (bParam <= 1) 1 else 2
                    LogManager.d("YahooAuctionParser", "「${condition.patternName}」: $modeName ($pageNum ページ目) 取得中...")

                    val priceMinParam = if (condition.minPrice > 0) "&aucminprice=${condition.minPrice}" else ""
                    val priceMaxParam = if (condition.maxPrice > 0) "&aucmaxprice=${condition.maxPrice}" else ""
                    
                    val categoryParam = if (condition.categories.isNotBlank()) {
                        val catId = condition.categories.split(",").firstOrNull()?.trim() ?: ""
                        if (catId.isNotEmpty()) "&auccat=$catId" else ""
                    } else {
                        ""
                    }

                    val yahooUrl = if (sortParam == "s3") {
                        "https://auctions.yahoo.co.jp/opensearch?p=$encodedQuery$priceMinParam$priceMaxParam$categoryParam&fixed=0&b=$bParam&is_postage_mode=1&n=50&select=5&mode=2"
                    } else {
                        val modeParam = if (sortParam.isNotEmpty()) "&$sortParam=1" else ""
                        "https://auctions.yahoo.co.jp/search/search?p=$encodedQuery$priceMinParam$priceMaxParam$categoryParam$modeParam&exflg=1&b=$bParam&n=100"
                    }

                    LogManager.d(
                        "YahooAuctionParser",
                        "ヤフオク検索URL ($modeName P$pageNum): $yahooUrl"
                    )

                    val doc =
                        Jsoup.connect(yahooUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(15000)
                            .get()

                    val htmlContent = doc.html()
                    LogManager.html("--- ヤフオク HTMLダンプ開始 [$modeName P$pageNum] (全文字数: ${htmlContent.length}) ---\n$htmlContent\n--- HTMLダンプ終了 ---")

                    val items =
                        doc.select(".Product, li.Product, div.Product, li[class*='Product'], div[class*='Product'], li.clst, div.clst, li[class*='clst']")

                    LogManager.d(
                        "YahooAuctionParser",
                        "ヤフオク取得件数 ($modeName P$pageNum): ${items.size}"
                    )

                    // 2ページ目でアイテムが一件も取得できなかった場合はループを抜けて次のソートへ
                    if (items.isEmpty() && pageNum > 1) break

                    for (element in items) {
                        try {
                            val titleEl =
                                element.select(
                                    ".Product__titleLink, a.Product__titleLink, a.thm, a[href*='/auction/']"
                                ).first() ?: continue

                            val rawTitle = titleEl.text()
                            val title: String = if (rawTitle != null) rawTitle.toString().trim() else ""

                            if (title.isBlank() || title.matches(Regex("^[0-9]+$")) || title == "送料無料" || title == "New!!") continue

                            val rawHref = titleEl.attr("href")
                            val itemUrl: String = if (rawHref != null) rawHref.toString() else ""

                            if (itemUrl.isBlank() || !itemUrl.contains("auction")) continue

                            val imgLinkEl = element.select("a.Product__imageLink, a[data-auction-id], a[href*='auction']")
                            val dataAuctionId = imgLinkEl.attr("data-auction-id")
                            
                            val finalItemId = if (!dataAuctionId.isNullOrBlank()) {
                                dataAuctionId
                            } else {
                                val urlWithoutQuery = if (itemUrl.contains("?")) {
                                    itemUrl.substring(0, itemUrl.indexOf("?"))
                                } else {
                                    itemUrl
                                }
                                if (urlWithoutQuery.contains("auction/")) {
                                    val pos = urlWithoutQuery.lastIndexOf("auction/")
                                    if (pos >= 0) urlWithoutQuery.substring(pos + 8) else itemUrl.hashCode().toString()
                                } else {
                                    urlWithoutQuery.hashCode().toString()
                                }
                            }

                            val finalUrl =
                                if (itemUrl.startsWith("http"))
                                    itemUrl
                                else
                                    "https://auctions.yahoo.co.jp$itemUrl"

                            if (excludedIds.contains(finalItemId) || excludedUrls.contains(finalUrl)) {
                                continue
                            }

                            if (fetchedItems.containsKey(finalItemId)) continue

                            val linkWithImg = element.select("a[data-auction-img]").first()
                            val dataAucImg = linkWithImg?.attr("data-auction-img")?.takeIf { !it.isNullOrEmpty() }

                            val imgEl = element.select("img").first()
                            val rawImg = dataAucImg 
                                ?: imgEl?.attr("data-src")?.takeIf { !it.isNullOrEmpty() }
                                ?: imgEl?.attr("data-original")?.takeIf { !it.isNullOrEmpty() }
                                ?: imgEl?.attr("src")?.takeIf { !it.isNullOrEmpty() }
                                ?: imgEl?.attr("srcset")?.takeIf { !it.isNullOrEmpty() }
                                ?: ""

                            val imageUrl: String = if (rawImg.startsWith("http")) rawImg else if (rawImg.startsWith("//")) "https:$rawImg" else rawImg

                            val rawCardText = element.text()
                            val cardText: String = if (rawCardText != null) rawCardText.toString() else ""

                            var currentPrice = 0
                            var promptPrice = 0

                            val currentMatch =
                                Regex("현재[:\\s]*([0-9,]+)円|現在[:\\s]*([0-9,]+)円")
                                    .find(cardText)

                            if (currentMatch != null) {
                                val priceStr = currentMatch.groupValues[1].ifEmpty { currentMatch.groupValues[2] }
                                currentPrice = priceStr.replace(",", "").toIntOrNull() ?: 0
                            }

                            val promptMatch =
                                Regex("即決[:\\s]*([0-9,]+)円")
                                    .find(cardText)

                            if (promptMatch != null) {
                                promptPrice =
                                    promptMatch.groupValues[1]
                                        .replace(",", "")
                                        .toIntOrNull() ?: 0
                            }

                            if (currentPrice == 0 && promptPrice == 0) {
                                val priceElement =
                                    element.select(
                                        ".Product__priceValue, .prc, span[class*='price']"
                                    ).first()

                                val priceTextRaw = priceElement?.text()
                                val priceText: String = if (priceTextRaw != null) priceTextRaw.toString() else ""

                                val rawMatch =
                                    Regex("([0-9,]+)")
                                        .find(priceText)

                                currentPrice =
                                    rawMatch
                                        ?.groupValues
                                        ?.get(1)
                                        ?.replace(",", "")
                                        ?.toIntOrNull()
                                        ?: 0
                            }

                            if (currentPrice == 0 && promptPrice == 0) continue

                            var bidCount = 0
                            val bidElement = element.select(".Product__bid, dd.Product__bid").first()
                            if (bidElement != null) {
                                bidCount = bidElement.text().trim().replace(",", "").toIntOrNull() ?: 0
                            } else {
                                val altBidElement = element.select("a[href*='bid_hist']")
                                if (altBidElement.isNotEmpty()) {
                                    bidCount = altBidElement.text().trim().replace(",", "").toIntOrNull() ?: 0
                                }
                            }

                            var endTime = ""
                            val bonusEl = element.select(".Product__bonus, [data-auction-endtime]").first()
                            val endtimeAttr = bonusEl?.attr("data-auction-endtime")?.takeIf { !it.isNullOrBlank() }
                                ?: titleEl.attr("data-auction-endtime")

                            if (!endtimeAttr.isNullOrBlank()) {
                                val timestamp = endtimeAttr.toLongOrNull()
                                if (timestamp != null && timestamp > 0) {
                                    val sdf = SimpleDateFormat("M月d日 HH:mm", Locale.JAPAN)
                                    endTime = sdf.format(Date(timestamp * 1000))
                                }
                            }

                            if (endTime.isBlank()) {
                                val timeCandidates = element.select(".Product__time, dd.Product__time, span[class*='time'], .Product__limit, time, dd, span, div")
                                for (el in timeCandidates) {
                                    val txt = el.text().trim()
                                    if (txt.contains("終了") || txt.contains("残り") || txt.contains("日") || txt.contains("時") || txt.contains("分")) {
                                        if (txt.length < 35 && !txt.contains("円") && !txt.contains("入札")) {
                                            endTime = txt
                                            break
                                        }
                                    }
                                }
                            }

                            if (endTime.isBlank()) {
                                val timeMatch = Regex("([0-9]+月[0-9]+日[^0-9]*[0-9]+時[0-9]+分|([0-9]+時)?([0-9]+分)?に終了|本日終了|残り[0-9日時間分]+|[0-9]+日[0-9]+時間)").find(cardText)
                                if (timeMatch != null) {
                                    endTime = timeMatch.groupValues[1]
                                }
                            }

                            if (endTime.isBlank()) {
                                endTime = "-"
                            }

                            val shippingInfo =
                                when {
                                    cardText.contains("送料無料") ||
                                            cardText.contains("送料込") ->
                                        "送料無料"

                                    cardText.contains("着払い") ->
                                        "着払い"

                                    else -> {
                                        val shipMatch =
                                            Regex(
                                                "送料[:\\s]*([0-9,]+)円"
                                            ).find(cardText)

                                        if (shipMatch != null) "${shipMatch.groupValues[1]}円" else "詳細確認"
                                    }
                                }

                            if (
                                element.select(
                                    ".Product__status--ended, .end"
                                ).isNotEmpty()
                            ) continue

                            fetchedItems[finalItemId] = ItemEntity(
                                itemId = finalItemId,
                                conditionId = condition.id.toLong(),
                                title = title,
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
                                endTime = endTime,
                                createdAt = currentTime
                            )

                        } catch (e: Exception) {
                            LogManager.e("YahooAuctionParser", "ヤフオクパース個別エラー", e)
                        }
                    }
                } catch (e: Exception) {
                    LogManager.e("YahooAuctionParser", "ヤフオク検索エラー ($modeName)", e)
                }
            }
        }

        return fetchedItems.values.toList()
    }
}
