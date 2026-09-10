package com.dsh.deepseekbalance.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.data.Dashboard
import com.dsh.deepseekbalance.data.Repository
import com.dsh.deepseekbalance.data.SyncResult
import com.dsh.deepseekbalance.databinding.ActivityMainBinding
import com.dsh.deepseekbalance.prefs.DsbPrefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repo: Repository get() = DsbApp.repo()
    private val modelAdapter = ModelUsageAdapter()

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户拒绝也不影响主要功能 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.swipe.setColorSchemeColors(getColor(R.color.ds_brand))
        binding.swipe.setOnRefreshListener { refresh(manual = true) }

        binding.rvModels.layoutManager = LinearLayoutManager(this)
        binding.rvModels.adapter = modelAdapter

        binding.btnChat.setOnClickListener {
            if (!ensureKey()) return@setOnClickListener
            startActivity(Intent(this, ChatTesterActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnPlatform.setOnClickListener {
            startActivity(Intent(this, PlatformLoginActivity::class.java))
        }
        binding.btnRefresh.setOnClickListener { refresh(manual = true) }

        collect { repo.dashboard.collectLatest { render(it) } }
        collect { repo.refreshing.collectLatest { binding.swipe.isRefreshing = it } }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            repo.loadDashboard()
            loadChart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!repo.hasKey && !repo.hasPlatform) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        maybeAskNotificationPermission()
        refresh(manual = false)
    }

    private fun ensureKey(): Boolean {
        if (repo.hasKey) return true
        toast(getString(R.string.not_logged_in))
        startActivity(Intent(this, SetupActivity::class.java))
        return false
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && DsbPrefs.get(this).notifyEnabled) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refresh(manual: Boolean) {
        if (!repo.hasKey && !repo.hasPlatform) {
            toast(getString(R.string.not_logged_in))
            return
        }
        lifecycleScope.launch {
            when (val result = repo.refresh(syncPlatform = true, syncBalance = true)) {
                is SyncResult.Ok -> {
                    loadChart()
                    if (manual && result.message.isNotBlank()) toast(result.message)
                    else if (manual) toast("已刷新")
                }

                is SyncResult.Failed -> if (manual) toast(result.message)
                SyncResult.NoKey -> if (manual) toast(getString(R.string.not_logged_in))
            }
        }
    }

    private fun loadChart() {
        lifecycleScope.launch {
            val points = repo.dailyPoints(14)
            binding.chart.setData(points)
            binding.tvChartEmpty.visibility =
                if (points.all { it.total == 0L }) View.VISIBLE else View.GONE
        }
    }

    private fun render(d: Dashboard) {
        val prefs = DsbPrefs.get(this)
        binding.tvLoginState.text = buildString {
            if (d.hasKey) append("API Key 已配置 · " + prefs.baseUrl)
            else append("未配置 API Key")
            append("  |  ")
            append(if (d.hasPlatform) getString(R.string.platform_connected) else "平台账号未登录")
        }

        // 余额
        val snapshot = d.balance
        if (snapshot != null) {
            binding.tvBalance.text = Fmt.money(snapshot.currency, snapshot.balance)
            binding.tvAvailable.text = if (snapshot.isAvailable) {
                getString(R.string.available_yes)
            } else {
                getString(R.string.available_no)
            }
            binding.tvAvailable.setTextColor(
                getColor(if (snapshot.isAvailable) R.color.ds_green else R.color.ds_red)
            )
        } else {
            binding.tvBalance.text = "--"
            binding.tvAvailable.text = if (d.hasKey) getString(R.string.never_updated)
            else getString(R.string.not_logged_in)
            binding.tvAvailable.setTextColor(getColor(R.color.ds_text_dim))
        }
        val granted = prefs.lastGrantedBalance.toDoubleOrNull()
        val toppedUp = prefs.lastToppedUpBalance.toDoubleOrNull()
        binding.tvGranted.text = if (granted != null) Fmt.money(prefs.lastCurrency, granted) else "--"
        binding.tvTopped.text = if (toppedUp != null) Fmt.money(prefs.lastCurrency, toppedUp) else "--"
        binding.tvLastUpdated.text = if (d.lastUpdated > 0) {
            getString(R.string.last_updated, Fmt.time(d.lastUpdated))
        } else {
            getString(R.string.never_updated)
        }

        // 平台账号状态
        val p = d.platform
        val warn = binding.rowPlatformWarning
        when {
            !d.hasPlatform -> {
                warn.visibility = View.VISIBLE
                binding.btnPlatform.text = getString(R.string.platform_connect)
                binding.tvPlatformWarning.text = getString(R.string.platform_not_connected)
            }

            p.authExpired -> {
                warn.visibility = View.VISIBLE
                binding.btnPlatform.text = getString(R.string.platform_relogin)
                binding.tvPlatformWarning.text = getString(R.string.platform_expired)
            }

            p.lastError.isNotBlank() -> {
                warn.visibility = View.VISIBLE
                binding.btnPlatform.text = getString(R.string.platform_relogin)
                binding.tvPlatformWarning.text = p.lastError
            }

            else -> warn.visibility = View.GONE
        }

        // 官方用量
        binding.tvTokensToday.text = Fmt.tokens(p.todayTokens)
        binding.tvTokensMonth.text = Fmt.tokens(p.monthTokens)
        binding.tvTodayBreakdown.text = getString(
            R.string.usage_input_breakdown,
            Fmt.tokens(p.todayInput),
            Fmt.tokens(p.todayCacheHit),
            Fmt.tokens(p.todayCacheMiss)
        ) + " · " + getString(R.string.usage_output_breakdown, Fmt.tokens(p.todayOutput))
        binding.tvTodayRequests.text = getString(R.string.usage_requests_label) + " " + p.todayRequests
        binding.tvTodayCost.text = getString(R.string.usage_today_cost) + " " + Fmt.money(p.currency, p.todayCost)

        binding.tvMonthBreakdown.text = getString(
            R.string.usage_input_breakdown,
            Fmt.tokens(p.monthInput),
            Fmt.tokens(p.monthCacheHit),
            Fmt.tokens(p.monthCacheMiss)
        ) + " · " + getString(R.string.usage_output_breakdown, Fmt.tokens(p.monthOutput))
        binding.tvMonthRequests.text = getString(R.string.usage_requests_label) + " " + p.monthRequests
        binding.tvMonthCost.text = getString(R.string.usage_month_cost) + " " + Fmt.money(p.currency, p.monthCost)
        binding.tvTokensTotal.text = getString(R.string.usage_total_label) + " " + Fmt.tokens(p.totalTokens)
        binding.tvTotalCost.text = "累计消费 " + Fmt.money(p.currency, p.totalCost)

        modelAdapter.submit(p.models)
        binding.tvModelsEmpty.visibility = if (p.models.isEmpty()) View.VISIBLE else View.GONE

        binding.tvPlatformSource.text = when {
            p.lastSync > 0 -> getString(R.string.platform_source) + " · 同步于 " + Fmt.time(p.lastSync)
            else -> getString(R.string.usage_no_platform_data)
        }

        binding.tvEstimateHint.visibility = if (d.estimating) View.VISIBLE else View.GONE
    }
}
