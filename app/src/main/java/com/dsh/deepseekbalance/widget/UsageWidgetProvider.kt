package com.dsh.deepseekbalance.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.sync.SyncWorker
import kotlinx.coroutines.launch

/**
 * 桌面小组件（三星 One UI / 原生桌面通用）。
 *
 * - 内容：账户余额、今日 tokens、本月 tokens、最近更新时间
 * - 点击刷新按钮：立即联网拉取余额并重算用量
 * - 系统定时：由 WorkManager（SyncWorker）周期刷新，最短 15 分钟
 */
class UsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        DsbApp.instance.appScope.launch {
            try {
                for (id in appWidgetIds) WidgetUpdater.update(context, appWidgetManager, id)
            } catch (t: Throwable) {
                Log.w(TAG, "onUpdate failed", t)
            } finally {
                pending.finish()
            }
        }
        SyncWorker.schedulePeriodic(context)
        SyncWorker.requestImmediate(context, "widget_update")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                val pending = goAsync()
                DsbApp.instance.appScope.launch {
                    try {
                        val manager = AppWidgetManager.getInstance(context)
                        val id = intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID
                        )
                        Log.i(TAG, "manual refresh: ${DsbApp.repo().refresh()}")
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && manager != null) {
                            WidgetUpdater.update(context, manager, id)
                        } else {
                            WidgetUpdater.updateAll(context)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "refresh failed", t)
                    } finally {
                        pending.finish()
                    }
                }
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val pending = goAsync()
                DsbApp.instance.appScope.launch {
                    try {
                        WidgetUpdater.updateAll(context)
                    } catch (t: Throwable) {
                        Log.w(TAG, "redraw failed", t)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        SyncWorker.schedulePeriodic(context)
        SyncWorker.requestImmediate(context, "widget_enabled")
    }

    override fun onDisabled(context: Context) {
        // 保留低频后台同步，用于余额告警通知。
        SyncWorker.schedulePeriodic(context)
    }

    companion object {
        const val TAG = "DsbWidget"
        const val ACTION_REFRESH = "com.dsh.deepseekbalance.action.WIDGET_REFRESH"

        /** 触发所有小组件重绘（数据变化时调用）。 */
        fun requestRedraw(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            val intent = Intent(context, UsageWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
