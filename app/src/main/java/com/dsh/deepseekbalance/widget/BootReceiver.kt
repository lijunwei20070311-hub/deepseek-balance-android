package com.dsh.deepseekbalance.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dsh.deepseekbalance.sync.SyncWorker

/** 开机 / App 更新后重新注册后台同步任务。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("DsbBoot", "reschedule sync after $action")
            SyncWorker.schedulePeriodic(context)
            SyncWorker.requestImmediate(context, "boot")
        }
    }
}
