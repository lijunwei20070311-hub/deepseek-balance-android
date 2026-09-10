package com.dsh.deepseekbalance.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * 后台同步：拉取余额 → （可选）用余额差额推算用量 → 重绘小组件。
 * 周期任务最短间隔为系统的 15 分钟。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = DsbApp.repo()
        return try {
            if (!repo.hasKey && !repo.hasPlatform) {
                WidgetUpdater.updateAll(applicationContext)
                return Result.success()
            }
            val result = repo.refresh(syncPlatform = true, syncBalance = true)
            Log.i(TAG, "sync result: $result")
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "sync failed", t)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "DsbSync"
        private const val PERIODIC_NAME = "dsb_periodic_sync"
        private const val IMMEDIATE_NAME = "dsb_immediate_sync"

        private fun constraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 注册/更新周期后台同步，间隔来自设置（不小于 15 分钟）。 */
        fun schedulePeriodic(context: Context) {
            val minutes = DsbPrefs.get(context).refreshMinutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                minutes.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 立即执行一次同步（例如打开 App、点击小组件刷新）。 */
        fun requestImmediate(context: Context, tag: String) {
            val prefs = DsbPrefs.get(context)
            if (prefs.apiKey.isBlank() && prefs.platformToken.isBlank()) return
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .addTag(tag)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
