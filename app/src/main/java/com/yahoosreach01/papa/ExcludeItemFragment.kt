//app/src/main/java/com/yahoosreach01/papa/ExcludeItemFragment.kt
//ver 1.01-19
package com.yahoosreach01.papa

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yahoosreach01.papa.databinding.FragmentSearchKeyBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExcludeItemFragment : Fragment() {
    private var _binding: FragmentSearchKeyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SearchResultAdapter // 既存のSearchResultAdapterを流用して除外品を表示

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fabAdd.visibility = View.GONE // 除外画面では＋ボタン不要

        val db = AppDatabase.getDatabase(requireContext())

        adapter = SearchResultAdapter(
            onItemClick = { item ->
                // タップで除外から復帰させるか確認
                AlertDialog.Builder(requireContext())
                    .setTitle("除外の解除")
                    .setMessage("「${item.title}」を検索結果に復帰させますか？")
                    .setPositiveButton("復帰する") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            db.itemDao().restoreItem(item.itemId)
                            Toast.makeText(requireContext(), "検索結果に復帰しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // すべての除外商品をリアルタイム取得して表示
        viewLifecycleOwner.lifecycleScope.launch {
            db.itemDao().getAllExcludedItems().collectLatest { items ->
                adapter.submitList(items)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
