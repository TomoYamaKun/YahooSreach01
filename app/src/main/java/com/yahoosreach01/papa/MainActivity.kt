//app/src/main/java/com/yahoosreach01/papa/MainActivity.kt
//ver 1.01-07
package com.yahoosreach01.papa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayoutMediator
import com.yahoosreach01.papa.databinding.ActivityMainBinding
import com.yahoosreach01.papa.utils.LogManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.tvVersion.text = "Ver: ${Constants.APP_VERSION}"

        setupTabs()

        LogManager.d("MainActivity", "onCreate completed")
    }

    private fun setupTabs() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        binding.viewPager.isUserInputEnabled = false

        val tabTitles = listOf("検索結果", "ログ", "検索キー", "除外キー", "除外商品")
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
}
