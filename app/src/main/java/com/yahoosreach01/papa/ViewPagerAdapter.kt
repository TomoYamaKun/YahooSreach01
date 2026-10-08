//app/src/main/java/com/yahoosreach01/papa/ViewPagerAdapter.kt
//ver 1.01-14
package com.yahoosreach01.papa

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4 // 4つのタブに変更（検索結果、ログ、検索キー、除外商品）

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SearchResultFragment()
            1 -> LogFragment()
            2 -> SearchKeyFragment() // ここに検索キーおよびパターンごとの除外キーが含まれます
            3 -> ExcludeItemFragment()
            else -> SearchResultFragment()
        }
    }
}
