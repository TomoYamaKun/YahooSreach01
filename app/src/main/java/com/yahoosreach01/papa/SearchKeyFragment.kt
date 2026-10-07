//app/src/main/java/com/yahoosreach01/papa/SearchKeyFragment.kt
//ver 1.01-07
package com.yahoosreach01.papa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.yahoosreach01.papa.databinding.FragmentTabPlaceholderBinding

class SearchKeyFragment : Fragment() {
    private var _binding: FragmentTabPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTabPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = "検索キー設定 (開発中)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
