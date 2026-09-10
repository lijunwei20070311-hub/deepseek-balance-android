package com.dsh.deepseekbalance.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.data.DsbDatabase
import com.dsh.deepseekbalance.data.PlatformDay
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.ui.Fmt
import com.dsh.deepseekbalance.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** 刷新小组件内容（余额来自 API Key，用量来自平台官方接口）。 */
object WidgetUpdater {

    private const val TAG = "DsbWidget"

    /** 渲染小组件所需的全部输入（便于单独测试渲染过程）。 */
    data class Snapshot(
        val hasApiKey: Boolean,
        val hasPlatform: Boolean,
        val currency: String,
        val balance: Double,
        val balanceError: String,
        val platformAuthExpired: Boolean,
        val platformError: String,
        val today: PlatformDay?,
        val monthTokens: Long,
        val monthInput: Long,
        val monthOutput: Long,
        val lastSync: Long
    )

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java))
        if (ids == null || ids.isEmpty()) return
        for (id in ids) update(context, manager, id)
    }

    suspend fun update(context: Context, manager: AppWidgetManager, id: Int) {
        try {
            val snapshot = collect(context)
            manager.updateAppWidget(id, buildViews(context, id, snapshot, minWidth(manager, id)))
        } catch (t: Throwable) {
            // 小组件绝不静默失败：把原因直接画在卡片上，方便定位。
            Log.e(TAG, "render widget $id failed", t)
            runCatching { manager.updateAppWidget(id, buildErrorViews(context, id, t)) }
        }
    }

    /** 读取需要展示的数据。 */
    suspend fun collect(context: Context): Snapshot {
        val prefs = DsbPrefs.get(context)
        val todayUtc = LocalDate.now(ZoneOffset.UTC).toString()
        val monthKey = YearMonth.now().toString()
        val dao = DsbDatabase.get(context).platformDao()
        val today = withContext(Dispatchers.IO) { dao.day(todayUtc) }
        val monthDays = withContext(Dispatchers.IO) { dao.daysFrom("$monthKey-00") }
        return Snapshot(
            hasApiKey = prefs.apiKey.isNotBlank(),
            hasPlatform = prefs.platformToken.isNotBlank(),
            currency = prefs.lastCurrency,
            balance = prefs.lastBalanceValue,
            balanceError = prefs.lastError,
            platformAuthExpired = prefs.platformAuthExpired,
            platformError = prefs.platformError,
            today = today,
            monthTokens = monthDays.sumOf { it.tokens },
            monthInput = monthDays.sumOf { it.cacheHit + it.cacheMiss },
            monthOutput = monthDays.sumOf { it.output },
            lastSync = monthDays.maxOfOrNull { it.updatedAt } ?: 0L
        )
    }

    private fun minWidth(manager: AppWidgetManager, id: Int): Int =
        manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            ?: 250

    /**
     * 生成小组件内容。这个函数不访问数据库/网络，可以直接在 JVM 测试里调用，
     * 因此「布局能不能解析、控件 id 有没有写错」都能被单元测试覆盖。
     */
    fun buildViews(context: Context, id: Int, s: Snapshot, minWidthDp: Int = 250): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        val compact = minWidthDp < 220

        views.setViewVisibility(R.id.widget_month_row, if (compact) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_divider, if (compact) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))

        // 余额
        views.setTextViewText(
            R.id.widget_balance,
            if (s.hasApiKey && s.balance >= 0) Fmt.money(s.currency, s.balance) else "--"
        )
        views.setTextViewText(
            R.id.widget_balance_sub,
            when {
                !s.hasApiKey && !s.hasPlatform -> context.getString(R.string.widget_not_login)
                s.balanceError.isNotBlank() -> s.balanceError
                !s.hasApiKey -> "余额：需配置 API Key"
                s.balance < 0 -> "余额：未同步"
                else -> Fmt.money(s.currency, s.balance) + " · 可用"
            }
        )

        // 今日（平台官方口径，按 UTC 切天）
        val today = s.today
        if (today != null) {
            views.setTextViewText(R.id.widget_today_total, Fmt.tokens(today.tokens) + " tokens")
            views.setTextViewText(
                R.id.widget_today_desc,
                "输入 ${Fmt.tokens(today.cacheHit + today.cacheMiss)} · 输出 ${Fmt.tokens(today.output)}"
            )
        } else {
            views.setTextViewText(R.id.widget_today_total, "-- tokens")
            views.setTextViewText(
                R.id.widget_today_desc,
                if (s.hasPlatform) "点 ⟳ 刷新" else context.getString(R.string.widget_not_login)
            )
        }

        // 本月
        views.setTextViewText(R.id.widget_month_total, Fmt.tokens(s.monthTokens) + " tokens")
        views.setTextViewText(
            R.id.widget_month_desc,
            "输入 ${Fmt.tokens(s.monthInput)} · 输出 ${Fmt.tokens(s.monthOutput)}"
        )

        views.setTextViewText(
            R.id.widget_updated,
            when {
                s.platformAuthExpired -> "登录已失效"
                !s.hasPlatform -> "未登录平台"
                s.lastSync > 0 -> context.getString(R.string.widget_updated_at, Fmt.time(s.lastSync))
                s.platformError.isNotBlank() -> s.platformError.take(18)
                else -> context.getString(R.string.widget_loading)
            }
        )

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, id))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending(context, id))
        return views
    }

    fun buildErrorViews(context: Context, id: Int, t: Throwable): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_balance, "--")
        views.setTextViewText(
            R.id.widget_balance_sub,
            "组件出错：" + (t.message ?: t.javaClass.simpleName)
        )
        views.setTextViewText(R.id.widget_today_total, "-- tokens")
        views.setTextViewText(R.id.widget_today_desc, "请打开 App 后点 ⟳ 重试")
        views.setTextViewText(R.id.widget_month_total, "-- tokens")
        views.setTextViewText(R.id.widget_month_desc, "")
        views.setTextViewText(R.id.widget_updated, Fmt.time(System.currentTimeMillis()))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, id))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending(context, id))
        return views
    }

    private fun openApp(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refreshPending(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, UsageWidgetProvider::class.java).apply {
            action = UsageWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id + 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
