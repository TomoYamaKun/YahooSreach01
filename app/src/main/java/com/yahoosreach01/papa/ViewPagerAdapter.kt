//app/src/main/java/com/yahoosreach01/papa/ViewPagerAdapter.kt
//ver 1.01-161
package com.yahoosreach01.papa

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 6 // 6つのタブに変更（検索結果、ログ、検索キー、除外商品、カテゴリ、ログイン）

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SearchResultFragment()
            1 -> LogFragment()
            2 -> SearchKeyFragment()
            3 -> ExcludeItemFragment()
            4 -> CategoryBrowserFragment() // ★新設したカテゴリ調査画面
            5 -> YahooLoginFragment()      // ログイン（WebViewタブ）
            else -> SearchResultFragment()
        }
    }
}
