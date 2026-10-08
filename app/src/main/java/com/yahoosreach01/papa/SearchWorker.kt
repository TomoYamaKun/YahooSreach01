//app/src/main/java/com/yahoosreach01/papa/SearchWorker.kt
//ver 1.01-15
package com.yahoosreach01.papa

import android.content.Context
import com.yahoosreach01.papa.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

object SearchWorker {

    /**
     * 登録されているすべての検索条件を元に、ヤフオク等から検索・収集を実行する
     */
    suspend fun executeSearch(context: Context) {
        val db = AppDatabase.getDatabase(context)
        // 実装を安全に行うため、まずはコルーチン＆ログ出力基盤を用いたワーカーの骨組みを作成します。
        LogManager.d("SearchWorker", "検索処理を開始します...")

        // TODO: SearchConditionDaoから条件を取得し、Jsoupでパース・DB保存するループ処理を順次構築します。
    }

    /**
     * 検索キーと除外キーから検索URLを構築する（除外キーには自動で `-` を付加）
     */
    fun buildSearchUrl(condition: SearchConditionEntity): String {
        // 検索キーはそのまま（例: "(A B)" やスペース区切り）
        val query = condition.searchKeys.trim()

        // 除外キーはスペース等で分割し、それぞれに必ず "-" を付加する仕様
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

        // 最終的なクエリ文字列（検索キー ＋ 除外キー(-付加)）
        val fullQuery = if (excludePart.isNotBlank()) "$query$excludePart" else query
        val encodedQuery = URLEncoder.encode(fullQuery, "UTF-8")

        // Yahoo!オークションの検索URLベース
        // 落札済み・購入されたものは対象外とするため、オークションの開催中・フリマの出品中に絞るパラメータを付加
        return "https://auctions.yahoo.co.jp/search/search?p=$encodedQuery&exflg=1&b=1&n=50"
    }
}
