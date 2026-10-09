//app/src/main/java/com/yahoosreach01/papa/LogFragment.kt
//ver 1.01-130
package com.yahoosreach01.papa

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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            binding.btnCopyNormal.setOnClickListener {
                context?.let { ctx -> LogManager.copyToClipboard(ctx, isHtml = false) }
            }

            binding.btnCopyHtml.setOnClickListener {
                context?.let { ctx -> LogManager.copyToClipboard(ctx, isHtml = true) }
            }

            binding.btnClear.setOnClickListener {
                LogManager.clearLogs()
                binding.tvLogContent.text = ""
                binding.tvHtmlContent.text = ""
            }

            refreshLogs()
        } catch (e: Exception) {
            LogManager.e("LogFragment", "初期化エラー", e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun refreshLogs() {
        try {
            binding.tvLogContent.text = LogManager.getLogs()
            binding.tvHtmlContent.text = if (LogManager.isHtmlDebugEnabled) {
                LogManager.getHtmlLogs()
            } else {
                "【HTMLデバッグは現在 OFF です（軽量化のため停止中）】\n※HTMLダンプを取得したい場合はコード内または設定で有効にしてください。"
            }
        } catch (e: Exception) {
            // 例外処理
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
