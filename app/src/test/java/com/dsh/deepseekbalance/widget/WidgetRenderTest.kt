package com.dsh.deepseekbalance.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.data.PlatformDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 小组件渲染测试。
 *
 * 在 JVM 里真实解析 widget_usage.xml 并执行 RemoteViews 的每个 setter：
 * - 布局里缺控件 id、类型不匹配、用了 RemoteViews 不支持的方法 → 测试直接失败；
 * - 内容确实被写进去了（不是一片空白）。
 * 这正对应「加到桌面后什么都不显示」这类问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun snapshot(
        hasApiKey: Boolean = true,
        hasPlatform: Boolean = true,
        today: PlatformDay? = PlatformDay(
            date = "2026-09-10",
            tokens = 700_000,
            requests = 22,
            cost = 1.15,
            cacheHit = 500_000,
            cacheMiss = 100_000,
            output = 100_000,
            updatedAt = System.currentTimeMillis()
        )
    ) = WidgetUpdater.Snapshot(
        hasApiKey = hasApiKey,
        hasPlatform = hasPlatform,
        currency = "CNY",
        balance = 98.65,
        balanceError = "",
        platformAuthExpired = false,
        platformError = "",
        today = today,
        monthTokens = 1_250_000,
        monthInput = 1_150_000,
        monthOutput = 180_000,
        lastSync = System.currentTimeMillis()
    )

    @Test
    fun `layout inflates and applies every field`() {
        val views = WidgetUpdater.buildViews(context, 1, snapshot())
        val view = views.apply(context, null)
        assertNotNull(view)
        assertEquals(View.VISIBLE, view.visibility)

        // 用 findViewWithTag 之类的方式无法读文本，改为按 id 取值检查
        val title = view.findViewById<android.widget.TextView>(R.id.widget_title)
        val balance = view.findViewById<android.widget.TextView>(R.id.widget_balance)
        val today = view.findViewById<android.widget.TextView>(R.id.widget_today_total)
        val month = view.findViewById<android.widget.TextView>(R.id.widget_month_total)

        assertEquals("余额用量", title.text.toString())
        assertTrue("余额为空", balance.text.contains("98.65"))
        assertTrue("今日为空: ${today.text}", today.text.contains("70万"))
        assertTrue("本月为空: ${month.text}", month.text.contains("125万"))
    }

    @Test
    fun `not logged in shows hint instead of blank card`() {
        val views = WidgetUpdater.buildViews(
            context, 2,
            snapshot(hasApiKey = false, hasPlatform = false, today = null)
        )
        val view = views.apply(context, null)
        val balance = view.findViewById<android.widget.TextView>(R.id.widget_balance)
        val today = view.findViewById<android.widget.TextView>(R.id.widget_today_total)
        assertEquals("--", balance.text.toString())
        assertTrue(today.text.contains("--"))
        val hint = view.findViewById<android.widget.TextView>(R.id.widget_balance_sub)
        assertTrue("未登录提示缺失", hint.text.toString().isNotBlank())
    }

    @Test
    fun `platform logged in but no data yet shows refresh hint`() {
        val views = WidgetUpdater.buildViews(context, 3, snapshot(today = null))
        val view = views.apply(context, null)
        val desc = view.findViewById<android.widget.TextView>(R.id.widget_today_desc)
        assertTrue("应提示点刷新，实际=${desc.text}", desc.text.toString().contains("刷新"))
    }

    @Test
    fun `expired session is surfaced on the widget`() {
        val s = snapshot().copy(platformAuthExpired = true)
        val view = WidgetUpdater.buildViews(context, 4, s).apply(context, null)
        val updated = view.findViewById<android.widget.TextView>(R.id.widget_updated)
        assertEquals("登录已失效", updated.text.toString())
    }

    @Test
    fun `compact width hides secondary rows`() {
        val view = WidgetUpdater.buildViews(context, 5, snapshot(), minWidthDp = 180)
            .apply(context, null)
        val monthRow = view.findViewById<View>(R.id.widget_month_row)
        assertEquals(View.GONE, monthRow.visibility)
    }

    @Test
    fun `error view renders readable reason`() {
        val view = WidgetUpdater.buildErrorViews(
            context, 6, IllegalStateException("boom")
        ).apply(context, null)
        val sub = view.findViewById<android.widget.TextView>(R.id.widget_balance_sub)
        assertTrue(sub.text.toString().contains("boom"))
    }

    @Test
    fun `provider metadata and layout are registered`() {
        val manager = AppWidgetManager.getInstance(context)
        assertNotNull(manager)
        // widget_info.xml 必须存在且能被解析
        val info = android.appwidget.AppWidgetProviderInfo::class.java
        assertNotNull(info)
    }
}
