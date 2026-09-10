package com.dsh.deepseekbalance.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.databinding.ActivitySettingsBinding
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.sync.SyncWorker
import com.dsh.deepseekbalance.widget.WidgetUpdater
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = DsbPrefs.get(this)
        val intervals = listOf(15, 30, 60, 120, 360)
        binding.etInterval.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, intervals.map { "$it 分钟" })
        )
        binding.etInterval.setText("${prefs.refreshMinutes} 分钟", false)
        binding.etCurrency.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("CNY", "USD"))
        )
        binding.etCurrency.setText(prefs.currency, false)
        binding.etPriceInput.setText(prefs.priceInput.toString())
        binding.etPriceOutput.setText(prefs.priceOutput.toString())
        binding.swNotify.isChecked = prefs.notifyEnabled
        binding.etThreshold.setText(prefs.notifyThreshold.toString())

        binding.btnSave.setOnClickListener { save(prefs, intervals) }
        binding.btnPlatform.setOnClickListener {
            startActivity(Intent(this, PlatformLoginActivity::class.java))
        }
        binding.btnClearPlatform.setOnClickListener {
            DsbApp.repo().clearPlatformToken()
            prefs.platformError = ""
            prefs.platformAuthExpired = false
            toast("已退出平台账号")
            refreshPlatformState()
        }
        binding.btnClearKey.setOnClickListener {
            prefs.clearApiKey()
            toast(getString(R.string.key_cleared))
            finish()
        }
        binding.tvWidgetHelp.text = getString(R.string.add_widget_help_body)
        refreshPlatformState()
    }

    override fun onResume() {
        super.onResume()
        refreshPlatformState()
    }

    private fun refreshPlatformState() {
        val prefs = DsbPrefs.get(this)
        binding.tvPlatformState.text = if (prefs.platformToken.isNotBlank()) {
            getString(R.string.platform_connected) +
                if (prefs.platformLastSync > 0) " · 同步于 " + Fmt.time(prefs.platformLastSync) else ""
        } else {
            getString(R.string.platform_not_connected)
        }
        binding.btnClearPlatform.isEnabled = prefs.platformToken.isNotBlank()
    }

    private fun save(prefs: DsbPrefs, intervals: List<Int>) {
        val minutes = binding.etInterval.text?.toString()
            ?.filter { it.isDigit() }?.toIntOrNull()
            ?: prefs.refreshMinutes
        prefs.refreshMinutes = if (intervals.contains(minutes)) minutes else minutes.coerceAtLeast(15)
        prefs.currency = binding.etCurrency.text?.toString()?.trim().orEmpty().ifBlank { "CNY" }
        prefs.priceInput = binding.etPriceInput.text?.toString()?.toDoubleOrNull() ?: prefs.priceInput
        prefs.priceOutput = binding.etPriceOutput.text?.toString()?.toDoubleOrNull() ?: prefs.priceOutput
        prefs.notifyEnabled = binding.swNotify.isChecked
        prefs.notifyThreshold = binding.etThreshold.text?.toString()?.toDoubleOrNull()
            ?: prefs.notifyThreshold

        SyncWorker.schedulePeriodic(this)
        lifecycleScope.launch {
            DsbApp.repo().loadDashboard()
            WidgetUpdater.updateAll(this@SettingsActivity)
        }
        toast(getString(R.string.settings_saved))
        finish()
    }
}
