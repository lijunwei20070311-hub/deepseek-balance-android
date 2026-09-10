package com.dsh.deepseekbalance

import android.app.Application
import com.dsh.deepseekbalance.data.DsbDatabase
import com.dsh.deepseekbalance.data.Repository
import com.dsh.deepseekbalance.notify.Notifier
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DsbApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifier.ensureChannel(this)
        appScope.launch {
            WidgetUpdater.updateAll(this@DsbApp)
        }
    }

    companion object {
        lateinit var instance: DsbApp
            private set

        fun db(): DsbDatabase = DsbDatabase.get(instance)
        fun prefs(): DsbPrefs = DsbPrefs.get(instance)
        fun repo(): Repository = Repository.get(instance)
    }
}
