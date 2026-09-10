package com.dsh.deepseekbalance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.api.BalanceInfo
import com.dsh.deepseekbalance.data.SyncResult
import com.dsh.deepseekbalance.databinding.ActivitySetupBinding
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.sync.SyncWorker
import com.dsh.deepseekbalance.widget.WidgetUpdater
import kotlinx.coroutines.launch

/**
 * 首次配置：API Key（用于余额）+ 平台账号登录（用于官方全量 tokens 用量）。
 */
class SetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = DsbPrefs.get(this)
        binding.etKey.setText(prefs.apiKey)
        binding.etBase.setText(prefs.baseUrl)
        binding.swAutoLedger.isChecked = prefs.autoLedger
        binding.swAutoLedger.visibility = View.GONE

        binding.btnSave.setOnClickListener { save() }
        binding.btnPlatformLogin.setOnClickListener {
            saveQuietly()
            startActivity(Intent(this, PlatformLoginActivity::class.java))
        }
        refreshPlatformState()
    }

    override fun onResume() {
        super.onResume()
        refreshPlatformState()
    }

    private fun refreshPlatformState() {
        val prefs = DsbPrefs.get(this)
        binding.tvPlatformState.text = if (prefs.platformToken.isNotBlank()) {
            getString(R.string.platform_connected)
        } else {
            getString(R.string.platform_not_connected)
        }
        binding.btnPlatformLogin.text = if (prefs.platformToken.isNotBlank()) {
            getString(R.string.platform_relogin)
        } else {
            getString(R.string.platform_connect)
        }
    }

    /** 只保存输入，不做网络校验（用于跳转登录前保留用户已填内容）。 */
    private fun saveQuietly() {
        val prefs = DsbPrefs.get(this)
        val key = binding.etKey.text?.toString()?.trim().orEmpty()
        if (key.isNotBlank()) prefs.apiKey = key
        prefs.baseUrl = binding.etBase.text?.toString()?.trim().orEmpty()
    }

    private fun save() {
        val key = binding.etKey.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) {
            binding.tilKey.error = getString(R.string.err_key_required)
            return
        }
        binding.tilKey.error = null
        val prefs = DsbPrefs.get(this)
        prefs.apiKey = key
        prefs.baseUrl = binding.etBase.text?.toString()?.trim().orEmpty()
        prefs.autoLedger = binding.swAutoLedger.isChecked

        binding.btnSave.isEnabled = false
        binding.tvResult.setTextColor(getColor(R.color.ds_text_dim))
        binding.tvResult.text = getString(R.string.refreshing)

        lifecycleScope.launch {
            when (val result = DsbApp.repo().refresh(syncPlatform = false, syncBalance = true)) {
                is SyncResult.Ok -> {
                    val info = result.balance
                    binding.tvResult.setTextColor(getColor(R.color.ds_green))
                    binding.tvResult.text = if (info != null) {
                        getString(R.string.key_saved) + " · 余额 " +
                            BalanceInfo.format(info.currency, info.totalValue)
                    } else {
                        getString(R.string.key_saved)
                    }
                    SyncWorker.schedulePeriodic(this@SetupActivity)
                    WidgetUpdater.updateAll(this@SetupActivity)
                    if (DsbPrefs.get(this@SetupActivity).platformToken.isNotBlank()) {
                        startActivity(
                            Intent(this@SetupActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    }
                }

                is SyncResult.Failed -> {
                    binding.tvResult.setTextColor(getColor(R.color.ds_red))
                    binding.tvResult.text = "验证失败：${result.message}"
                }

                SyncResult.NoKey -> {
                    binding.tvResult.setTextColor(getColor(R.color.ds_red))
                    binding.tvResult.text = getString(R.string.err_key_required)
                }
            }
            binding.btnSave.isEnabled = true
        }
    }
}
