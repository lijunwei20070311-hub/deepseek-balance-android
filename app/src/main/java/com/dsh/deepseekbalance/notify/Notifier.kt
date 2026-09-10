package com.dsh.deepseekbalance.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.api.BalanceInfo
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.ui.MainActivity

object Notifier {

    private const val CHANNEL_ID = "dsb_balance_alert"
    private const val NOTIFY_ID = 1001
    private const val MIN_INTERVAL_MS = 12 * 3600 * 1000L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notify_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun maybeNotifyLowBalance(context: Context, info: BalanceInfo) {
        val prefs = DsbPrefs.get(context)
        if (!prefs.notifyEnabled) return
        val low = !info.isAvailable || info.totalValue < prefs.notifyThreshold
        if (!low) return
        val now = System.currentTimeMillis()
        val last = prefs.plainGetLong("last_notify_at", 0L)
        if (now - last < MIN_INTERVAL_MS) return
        prefs.plainPutLong("last_notify_at", now)

        val text = BalanceInfo.format(info.currency, info.totalValue)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_balance)
            .setContentTitle(context.getString(R.string.notify_title))
            .setContentText("当前余额 $text")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
        }
    }
}
