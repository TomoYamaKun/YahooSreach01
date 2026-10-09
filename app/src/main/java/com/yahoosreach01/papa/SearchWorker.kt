//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-93
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

            // 1. ヤフオク検索
            if (condition.targetService != "fleamarket") {

                try {
                    val priceMinParam = if (condition.minPrice > 0) "&aucminprice=${condition.minPrice}" else ""
                    val priceMaxParam = if (condition.maxPrice > 0) "&aucmaxprice=${condition.maxPrice}" else ""

                    val yahooUrl =
                        "https://auctions.yahoo.co.jp/search/search?p=$encodedQuery$priceMinParam$priceMaxParam&exflg=1&b=1&n=50"

                    LogManager.d(
                        "SearchWorker",
                        "ヤフオク検索URL: $yahooUrl"
                    )

                    val doc =
                        Jsoup.connect(yahooUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(15000)
                            .get()

                    val items =
                        doc.select("li[class*='sc-'], .Product, li.clst, div.Product, div[class*='Product__'], ul > li")

                    LogManager.d(
                        "SearchWorker",
                        "ヤフオク取得件数: ${items.size}"
                    )

                    val htmlContent = doc.html()
                    LogManager.html("--- ヤフオク HTMLダンプ開始 (全文字数: ${htmlContent.length}) ---\n$htmlContent\n--- HTMLダンプ終了 ---")

                    for (element in items) {

                        try {

                            val titleEl =
                                element.select(
                                    "a[href*='/auction/'], .Product__titleLink, a.thm, a[href*='auction'], p > a"
                                ).first() ?: continue

                            val rawTitle = titleEl.text()
                            val title: String = if (rawTitle != null) rawTitle.toString() else ""

                            if (title.isBlank()) continue

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

                            // 画像取得の優先順位を修正：HTMLダンプに存在する data-auction-img を最優先で取得
                            val linkWithImg = element.select("a[data-auction-img]").first()
                            val dataAucImg = linkWithImg?.attr("data-auction-img")?.takeIf { !it.isNullOrEmpty() }

                            val imgEl = element.select("img").first()
                            val rawImg = dataAucImg 
                                ?: imgEl?.attr("data-src")?.takeIf { !it.isNullOrEmpty() }
                                ?: imgEl?.attr("data-original")?.takeIf { !it.isNullOrEmpty() }
                                ?: imgEl?.attr("src")?.takeIf { !it.isNullOrEmpty() }
                                ?: imgEl?.attr("srcset")?.takeIf { !it.isNullOrEmpty() }
                                ?: ""

                            val imageUrl: String = if (rawImg != null) rawImg.toString() else ""

                            val rawCardText = element.text()
                            val cardText: String = if (rawCardText != null) rawCardText.toString() else ""

                            var currentPrice = 0
                            var promptPrice = 0

                            val currentMatch =
                                Regex("現在[:\\s]*([0-9,]+)円")
                                    .find(cardText)

                            if (currentMatch != null) {
                                currentPrice =
                                    currentMatch.groupValues[1]
                                        .replace(",", "")
                                        .toIntOrNull() ?: 0
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

                            val finalUrl =
                                if (itemUrl.startsWith("http"))
                                    itemUrl
                                else
                                    "https://auctions.yahoo.co.jp$itemUrl"

                            val finalImgUrl =
                                if (imageUrl.startsWith("http"))
                                    imageUrl
                                else if (imageUrl.startsWith("//"))
                                    "https:$imageUrl"
                                else
                                    imageUrl

                            fetchedItems.add(
                                ItemEntity(
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
                            )

                        } catch (e: Exception) {
                            LogManager.e(
                                "SearchWorker",
                                "ヤフオクパースエラー",
                                e
                            )
                        }
                    }

                } catch (e: Exception) {
                    LogManager.e(
                        "SearchWorker",
                        "ヤフオク検索エラー",
                        e
                    )
                }
            }

            // 2. フリマ検索
            if (condition.targetService != "auction") {

                try {

                    val fleaUrl =
                        "https://paypayfleamarket.yahoo.co.jp/search/$encodedQuery"

                    LogManager.d(
                        "SearchWorker",
                        "フリマ検索URL: $fleaUrl"
                    )

                    val doc =
                        Jsoup.connect(fleaUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(15000)
                            .get()

                    val fleaItems =
                        doc.select(
                            "a[href*='/item/'], div[data-reactid], li"
                        )

                    for (element in fleaItems) {

                        try {

                            val aEl =
                                if (element.tagName() == "a")
                                    element
                                else
                                    element.select("a").first()

                            val rawHref = aEl?.attr("href")
                            val itemUrl: String = if (rawHref != null) rawHref.toString() else ""

                            if (
                                itemUrl.isBlank() ||
                                !itemUrl.contains("/item/")
                            ) continue

                            val titleElement =
                                element.select("div, span").first()

                            val rawTitle = titleElement?.text()
                            val title: String = if (rawTitle != null) rawTitle.toString() else ""

                            if (
                                title.isBlank() ||
                                title.length < 3
                            ) continue

                            val fleaUrlWithoutQuery =
                                if (itemUrl.contains("?")) {
                                    itemUrl.substring(
                                        0,
                                        itemUrl.indexOf("?")
                                    )
                                } else {
                                    itemUrl
                                }

                            val fleaId =
                                if (fleaUrlWithoutQuery.contains("item/")) {

                                    val pos =
                                        fleaUrlWithoutQuery.lastIndexOf("item/")

                                    if (pos >= 0) {
                                        fleaUrlWithoutQuery.substring(pos + 5)
                                    } else {
                                        ""
                                    }

                                } else {
                                    ""
                                }

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

                            val imageUrl: String = if (rawImg != null) rawImg.toString() else ""

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

                            val finalUrl =
                                if (itemUrl.startsWith("http"))
                                    itemUrl
                                else
                                    "https://paypayfleamarket.yahoo.co.jp$itemUrl"

                            val finalImgUrl =
                                if (imageUrl.startsWith("http"))
                                    imageUrl
                                else if (imageUrl.startsWith("//"))
                                    "https:$imageUrl"
                                else
                                    imageUrl

                            fetchedItems.add(
                                ItemEntity(
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
                            )

                        } catch (_: Exception) {
                        }
                    }

                } catch (e: Exception) {
                    LogManager.e(
                        "SearchWorker",
                        "フリマ検索エラー",
                        e
                    )
                }
            }

            // 3. ヤフオク・フリマ統合ソートの実行
            val sortedItems = when (condition.sortOrder) {
                // 価格安い順
                "s" -> fetchedItems.sortedBy { 
                    val p = if (it.currentPrice > 0) it.currentPrice else it.promptDecisionPrice
                    if (p > 0) p else Int.MAX_VALUE 
                }
                // 入札多い順
                "b" -> fetchedItems.sortedByDescending { it.bidCount }
                // 新着順・デフォルト
                else -> fetchedItems.sortedByDescending { it.createdAt }
            }

            // 4. ソート済みのリストをデータベースへ一括挿入
            for (item in sortedItems) {
                db.itemDao().insertItem(item)
            }

            LogManager.d(
                "SearchWorker",
                "「${condition.patternName}」の全検索・統合ソート・保存が完了しました（合計: ${sortedItems.size}件）"
            )
        }
    }

    fun buildSearchUrl(
        condition: SearchConditionEntity
    ): String {
        return ""
    }
}
