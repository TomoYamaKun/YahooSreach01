(//app/src/main/java/com/yahoosreach01/papa/ui/ViewPagerAdapter.kt
//ver 1.01-04
package com.yahoosreach01.papa.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SearchResultFragment()
            1 -> LogFragment()
            2 -> SearchKeyFragment()
            3 -> ExcludeKeyFragment()
            4 -> ExcludeItemFragment()
            else -> SearchResultFragment()
        }
    }
}
