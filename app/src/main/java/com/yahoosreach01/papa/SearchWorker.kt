//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-65
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

            // 1. ヤフオク検索
            if (condition.targetService != "fleamarket") {

                try {

                    val yahooUrl =
                        "https://auctions.yahoo.co.jp/search/search?p=$encodedQuery&exflg=1&b=1&n=50"

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
                        doc.select(".Product, li.clst, div.Product")

                    LogManager.d(
                        "SearchWorker",
                        "ヤフオク取得件数: ${items.size}"
                    )

                    for (element in items) {

                        try {

                            val titleEl =
                                element.select(
                                    ".Product__titleLink, a.thm, a[href*='auction']"
                                )

                            val rawTitle = titleEl.text()
                            val title: String = if (rawTitle != null) rawTitle.toString() else ""

                            if (title.isBlank()) continue

                            val rawHref = titleEl.attr("href")
                            val itemUrl: String = if (rawHref != null) rawHref.toString() else ""

                            if (itemUrl.isBlank()) continue

                            val urlWithoutQuery =
                                if (itemUrl.contains("?")) {
                                    itemUrl.substring(
                                        0,
                                        itemUrl.indexOf("?")
                                    )
                                } else {
                                    itemUrl
                                }

                            val itemId =
                                if (urlWithoutQuery.contains("auction/")) {

                                    val pos =
                                        urlWithoutQuery.lastIndexOf("auction/")

                                    if (pos >= 0) {
                                        urlWithoutQuery.substring(pos + 8)
                                    } else {
                                        ""
                                    }

                                } else {
                                    urlWithoutQuery.hashCode().toString()
                                }

                            val finalItemId =
                                if (itemId.isBlank())
                                    itemUrl.hashCode().toString()
                                else
                                    itemId

                            val imgEl =
                                element.select("img")

                            val rawImg = imgEl.attr("data-src").takeIf { !it.isNullOrEmpty() } ?: imgEl.attr("src")
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
                                        ".Product__priceValue, .prc"
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

                            val bidCount = 0

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

                                        if (shipMatch != null)
                                            "${shipMatch.groupValues[1]}円"
                                        else
                                            "詳細確認"
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
                                else
                                    "https:$imageUrl"

                            db.itemDao().insertItem(
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

                            if (fleaId.isBlank()) continue

                            val imgEl =
                                element.select("img")

                            val rawImg = imgEl.attr("data-src").takeIf { !it.isNullOrEmpty() } ?: imgEl.attr("src")
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
                                else
                                    "https:$imageUrl"

                            db.itemDao().insertItem(
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

            LogManager.d(
                "SearchWorker",
                "「${condition.patternName}」の全検索・保存が完了しました"
            )
        }
    }

    fun buildSearchUrl(
        condition: SearchConditionEntity
    ): String {
        return ""
    }
}
