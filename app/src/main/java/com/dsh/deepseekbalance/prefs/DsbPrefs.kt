package com.dsh.deepseekbalance.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 本机加密存储：API Key 只保存在设备上（EncryptedSharedPreferences，AES256-GCM）。
 * 其余非敏感设置放在普通 SharedPreferences 中。
 */
class DsbPrefs private constructor(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("dsb_settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context, "dsb_master_key")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dsb_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as SharedPreferences
    }.getOrElse {
        // 极少数设备若加密存储不可用，退化为私有目录存储，保证功能可用。
        context.getSharedPreferences("dsb_secure_fallback", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = secure.getString(KEY_API, "") ?: ""
        set(value) = secure.edit().putString(KEY_API, value.trim()).apply()

    /**
     * platform.deepseek.com 的登录态 userToken。
     * 只有它能读到「账号下全部 tokens 用量」（API Key 只能读余额）。
     * 网页登录后自动写入，也可手动粘贴；永远不出本机。
     */
    var platformToken: String
        get() = secure.getString(KEY_PLATFORM, "") ?: ""
        set(value) = secure.edit().putString(KEY_PLATFORM, value.trim()).apply()

    var platformError: String
        get() = plain.getString(K_PLATFORM_ERROR, "") ?: ""
        set(value) = plain.edit().putString(K_PLATFORM_ERROR, value).apply()

    var platformAuthExpired: Boolean
        get() = plain.getBoolean(K_PLATFORM_EXPIRED, false)
        set(value) = plain.edit().putBoolean(K_PLATFORM_EXPIRED, value).apply()

    var platformLastSync: Long
        get() = plain.getLong(K_PLATFORM_SYNC, 0L)
        set(value) = plain.edit().putLong(K_PLATFORM_SYNC, value).apply()

    var baseUrl: String
        get() = plain.getString(K_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
        set(value) {
            val v = value.trim().trimEnd('/').ifBlank { DEFAULT_BASE }
            plain.edit().putString(K_BASE, v).apply()
        }

    /** 后台刷新间隔（分钟），系统下限 15 分钟。 */
    var refreshMinutes: Int
        get() = plain.getInt(K_INTERVAL, 30)
        set(value) = plain.edit().putInt(K_INTERVAL, value.coerceAtLeast(15)).apply()

    var currency: String
        get() = plain.getString(K_CURRENCY, "CNY") ?: "CNY"
        set(value) = plain.edit().putString(K_CURRENCY, value).apply()

    /** 输入价格（每百万 tokens，币种与账户余额一致）。 */
    var priceInput: Double
        get() = plain.getFloat(K_PRICE_IN, 3.0f).toDouble()
        set(value) = plain.edit().putFloat(K_PRICE_IN, value.toFloat()).apply()

    /** 输出价格（每百万 tokens）。 */
    var priceOutput: Double
        get() = plain.getFloat(K_PRICE_OUT, 9.0f).toDouble()
        set(value) = plain.edit().putFloat(K_PRICE_OUT, value.toFloat()).apply()

    /** 是否用余额差额自动推算 tokens 用量。 */
    var autoLedger: Boolean
        get() = plain.getBoolean(K_AUTO_LEDGER, true)
        set(value) = plain.edit().putBoolean(K_AUTO_LEDGER, value).apply()

    var notifyEnabled: Boolean
        get() = plain.getBoolean(K_NOTIFY, true)
        set(value) = plain.edit().putBoolean(K_NOTIFY, value).apply()

    var notifyThreshold: Double
        get() = plain.getFloat(K_THRESHOLD, 10.0f).toDouble()
        set(value) = plain.edit().putFloat(K_THRESHOLD, value.toFloat()).apply()

    /** 最近一次成功刷新的时间戳（毫秒）。 */
    var lastUpdated: Long
        get() = plain.getLong(K_LAST_UPDATED, 0L)
        set(value) = plain.edit().putLong(K_LAST_UPDATED, value).apply()

    var lastBalanceText: String
        get() = plain.getString(K_LAST_BALANCE, "") ?: ""
        set(value) = plain.edit().putString(K_LAST_BALANCE, value).apply()

    var lastCurrency: String
        get() = plain.getString(K_LAST_CURRENCY, "CNY") ?: "CNY"
        set(value) = plain.edit().putString(K_LAST_CURRENCY, value).apply()

    var lastBalanceValue: Double
        get() = plain.getFloat(K_LAST_BALANCE_VALUE, -1f).toDouble()
        set(value) = plain.edit().putFloat(K_LAST_BALANCE_VALUE, value.toFloat()).apply()

    var lastError: String
        get() = plain.getString(K_LAST_ERROR, "") ?: ""
        set(value) = plain.edit().putString(K_LAST_ERROR, value).apply()

    var lastGrantedBalance: String
        get() = plain.getString(K_LAST_GRANTED, "") ?: ""
        set(value) = plain.edit().putString(K_LAST_GRANTED, value).apply()

    var lastToppedUpBalance: String
        get() = plain.getString(K_LAST_TOPPED_UP, "") ?: ""
        set(value) = plain.edit().putString(K_LAST_TOPPED_UP, value).apply()

    fun plainGetLong(key: String, def: Long): Long = plain.getLong(key, def)

    fun plainPutLong(key: String, value: Long) {
        plain.edit().putLong(key, value).apply()
    }

    fun clearApiKey() {
        secure.edit().remove(KEY_API).apply()
    }

    fun clearPlatformToken() {
        secure.edit().remove(KEY_PLATFORM).apply()
        platformError = ""
        platformAuthExpired = false
    }

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_PLATFORM = "platform_user_token"
        private const val K_PLATFORM_ERROR = "platform_error"
        private const val K_PLATFORM_EXPIRED = "platform_auth_expired"
        private const val K_PLATFORM_SYNC = "platform_last_sync"
        private const val K_BASE = "base_url"
        private const val K_INTERVAL = "refresh_minutes"
        private const val K_CURRENCY = "currency"
        private const val K_PRICE_IN = "price_input"
        private const val K_PRICE_OUT = "price_output"
        private const val K_AUTO_LEDGER = "auto_ledger"
        private const val K_NOTIFY = "notify_enabled"
        private const val K_THRESHOLD = "notify_threshold"
        private const val K_LAST_UPDATED = "last_updated"
        private const val K_LAST_BALANCE = "last_balance_text"
        private const val K_LAST_CURRENCY = "last_currency"
        private const val K_LAST_BALANCE_VALUE = "last_balance_value"
        private const val K_LAST_ERROR = "last_error"
        private const val K_LAST_GRANTED = "last_granted_balance"
        private const val K_LAST_TOPPED_UP = "last_topped_up_balance"

        const val DEFAULT_BASE = "https://api.deepseek.com"

        @Volatile
        private var cached: DsbPrefs? = null

        fun get(context: Context): DsbPrefs =
            cached ?: synchronized(this) {
                cached ?: DsbPrefs(context.applicationContext).also { cached = it }
            }
    }
}
