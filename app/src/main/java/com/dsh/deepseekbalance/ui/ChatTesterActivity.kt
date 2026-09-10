package com.dsh.deepseekbalance.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.api.DeepSeekClient
import com.dsh.deepseekbalance.databinding.ActivityChatTesterBinding
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.widget.WidgetUpdater
import kotlinx.coroutines.launch

/**
 * 内置调用测试：想立刻得到「精确」的 token 用量时，用这里的输入框直接调用一次 API，
 * 服务端返回的 usage 会被写入本地账本，并同步到桌面小组件。
 */
class ChatTesterActivity : BaseActivity() {

    private lateinit var binding: ActivityChatTesterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatTesterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvOutput.text = "在这里输入内容发送给 DeepSeek，返回的 token 用量会记入本地账本。"
        binding.btnSend.setOnClickListener { send() }
    }

    private fun send() {
        val prompt = binding.etInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) return
        val prefs = DsbPrefs.get(this)
        if (prefs.apiKey.isBlank()) {
            toast(getString(R.string.not_logged_in))
            return
        }
        binding.btnSend.isEnabled = false
        binding.tvOutput.text = getString(R.string.refreshing)
        lifecycleScope.launch {
            try {
                val result = DeepSeekClient.chat(
                    baseUrl = prefs.baseUrl,
                    apiKey = prefs.apiKey,
                    model = "deepseek-v4-flash",
                    prompt = prompt
                )
                DsbApp.repo().recordChatUsage(result.usage)
                binding.tvOutput.text = result.content.ifBlank { "(空响应)" } + "\n\n" +
                    getString(
                        R.string.chat_usage_note,
                        result.usage.promptTokens,
                        result.usage.completionTokens
                    )
                binding.etInput.setText("")
                WidgetUpdater.updateAll(this@ChatTesterActivity)
            } catch (t: Throwable) {
                binding.tvOutput.text = getString(R.string.error_generic, t.message ?: "unknown")
            } finally {
                binding.btnSend.isEnabled = true
            }
        }
    }
}
