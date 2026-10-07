   //app/src/main/java/com/yahoosreach01/papa/ui/LogFragment.kt
//ver 1.01-04
package com.yahoosreach01.papa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.yahoosreach01.papa.databinding.FragmentLogBinding
import com.yahoosreach01.papa.utils.LogManager

class LogFragment : Fragment() {
    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvLogContent.text = LogManager.getLogs()

        // 画面全体タップでクリップボードへコピー（要件17）
        binding.root.setOnClickListener {
            LogManager.copyToClipboard(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        // タブ切り替え時に最新ログを反映
        binding.tvLogContent.text = LogManager.getLogs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
