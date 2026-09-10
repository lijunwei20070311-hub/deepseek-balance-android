package com.dsh.deepseekbalance.data

import android.content.Context
import com.dsh.deepseekbalance.api.BalanceInfo
import com.dsh.deepseekbalance.api.ChatUsage
import com.dsh.deepseekbalance.api.DeepSeekClient
import com.dsh.deepseekbalance.api.PlatformApi
import com.dsh.deepseekbalance.api.PlatformClient
import com.dsh.deepseekbalance.notify.Notifier
import com.dsh.deepseekbalance.prefs.DsbPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** 一次刷新的结果，供界面提示使用。 */
sealed interface SyncResult {
    data class Ok(
        val balance: BalanceInfo?,
        val estimatedTokens: Long,
        val platformOk: Boolean,
        val message: String = ""
    ) : SyncResult {
        companion object {
            val EMPTY = Ok(null, 0L, false)
        }
    }

    data class Failed(val message: String) : SyncResult
    data object NoKey : SyncResult
}

/** 平台官方用量总览（口径与开放平台后台完全一致）。 */
data class PlatformSummary(
    val connected: Boolean = false,
    val todayTokens: Long = 0,
    val todayRequests: Long = 0,
    val todayCost: Double = 0.0,
    val todayCacheHit: Long = 0,
    val todayCacheMiss: Long = 0,
    val todayOutput: Long = 0,
    val monthTokens: Long = 0,
    val monthRequests: Long = 0,
    val monthCost: Double = 0.0,
    val monthCacheHit: Long = 0,
    val monthCacheMiss: Long = 0,
    val monthOutput: Long = 0,
    val totalTokens: Long = 0,
    val totalRequests: Long = 0,
    val totalCost: Double = 0.0,
    val currency: String = "CNY",
    val models: List<PlatformApi.ModelUsage> = emptyList(),
    val lastSync: Long = 0L,
    val lastError: String = "",
    val authExpired: Boolean = false
) {
    val todayInput: Long get() = todayCacheHit + todayCacheMiss
    val monthInput: Long get() = monthCacheHit + monthCacheMiss
}

/** 仪表盘聚合数据。 */
data class Dashboard(
    val balance: BalanceSnapshot? = null,
    val platform: PlatformSummary = PlatformSummary(),
    val local: Totals = Totals(),
    val localToday: Totals = Totals(),
    val lastUpdated: Long = 0L,
    val lastError: String = "",
    val hasKey: Boolean = false,
    val hasPlatform: Boolean = false
) {
    /** 是否只能靠余额差额估算（没有平台登录态时）。 */
    val estimating: Boolean get() = !platform.connected && local.totalTokens > 0
}

data class DayPoint(val day: Long, val total: Long, val prompt: Long, val completion: Long)

class Repository private constructor(private val context: Context) {

    private val db = DsbDatabase.get(context)
    private val prefs = DsbPrefs.get(context)
    private val syncMutex = Mutex()

    private val _dashboard = MutableStateFlow(Dashboard())
    val dashboard: StateFlow<Dashboard> = _dashboard.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val hasKey: Boolean get() = prefs.apiKey.isNotBlank()
    val hasPlatform: Boolean get() = prefs.platformToken.isNotBlank()

    // ---------------------------------------------------------------- 聚合

    suspend fun loadDashboard() {
        val today = LocalDate.now()
        val todayDay = today.toEpochDay()
        val monthStart = YearMonth.from(today).atDay(1).toEpochDay()
        val localToday = db.usageDao().totalsBetween(todayDay, todayDay)
        val localAll = db.usageDao().totalsAll()
        val platform = buildPlatformSummary()
        _dashboard.value = Dashboard(
            balance = db.balanceDao().latest(),
            platform = platform,
            local = localAll,
            localToday = localToday,
            lastUpdated = if (platform.lastSync > 0) platform.lastSync else prefs.lastUpdated,
            lastError = prefs.lastError,
            hasKey = hasKey,
            hasPlatform = hasPlatform
        )
    }

    private suspend fun buildPlatformSummary(): PlatformSummary {
        val days = db.platformDao().allDays()
        if (days.isEmpty()) {
            return PlatformSummary(
                connected = hasPlatform,
                currency = prefs.currency,
                lastError = prefs.platformError,
                authExpired = prefs.platformAuthExpired
            )
        }
        val todayUtc = LocalDate.now(ZoneOffset.UTC).toString()
        val todayRow = days.firstOrNull { it.date == todayUtc }
        val monthKey = YearMonth.now().toString()
        val monthRows = days.filter { it.date.startsWith(monthKey) }
        val models = db.platformDao().modelsOf(monthKey)
            .map { PlatformApi.ModelUsage(it.model, it.tokens, it.requests, it.cost) }
        return PlatformSummary(
            connected = hasPlatform,
            todayTokens = todayRow?.tokens ?: 0L,
            todayRequests = todayRow?.requests ?: 0L,
            todayCost = todayRow?.cost ?: 0.0,
            todayCacheHit = todayRow?.cacheHit ?: 0L,
            todayCacheMiss = todayRow?.cacheMiss ?: 0L,
            todayOutput = todayRow?.output ?: 0L,
            monthTokens = monthRows.sumOf { it.tokens },
            monthRequests = monthRows.sumOf { it.requests },
            monthCost = monthRows.sumOf { it.cost },
            monthCacheHit = monthRows.sumOf { it.cacheHit },
            monthCacheMiss = monthRows.sumOf { it.cacheMiss },
            monthOutput = monthRows.sumOf { it.output },
            totalTokens = days.sumOf { it.tokens },
            totalRequests = days.sumOf { it.requests },
            totalCost = days.sumOf { it.cost },
            currency = prefs.currency,
            models = models,
            lastSync = days.maxOf { it.updatedAt },
            lastError = prefs.platformError,
            authExpired = prefs.platformAuthExpired
        )
    }

    /** 近 N 天用量：优先用平台官方按天数据。 */
    suspend fun dailyPoints(days: Int = 14): List<DayPoint> {
        val end = LocalDate.now()
        val start = end.minusDays((days - 1).toLong())
        val platform = db.platformDao().daysFrom(start.toString())
        if (platform.isNotEmpty()) {
            val map = platform.associateBy { it.date }
            return (0 until days).map { offset ->
                val d = start.plusDays(offset.toLong())
                val row = map[d.toString()]
                DayPoint(
                    day = d.toEpochDay(),
                    total = row?.tokens ?: 0L,
                    prompt = (row?.cacheHit ?: 0L) + (row?.cacheMiss ?: 0L),
                    completion = row?.output ?: 0L
                )
            }
        }
        val raw = db.usageDao().dailyFrom(start.toEpochDay()).associateBy { it.epochDay }
        return (0 until days).map { offset ->
            val d = start.plusDays(offset.toLong())
            val agg = raw[d.toEpochDay()]
            DayPoint(
                day = d.toEpochDay(),
                total = agg?.totalTokens ?: 0L,
                prompt = agg?.promptTokens ?: 0L,
                completion = agg?.completionTokens ?: 0L
            )
        }
    }

    suspend fun recentRecords(limit: Int = 50): List<UsageRecord> = db.usageDao().recent(limit)

    suspend fun deleteRecord(id: Long) {
        db.usageDao().delete(id)
        loadDashboard()
    }

    // ---------------------------------------------------------------- 平台登录态

    /** 校验并保存平台登录态（网页登录或手动粘贴都走这里）。 */
    suspend fun savePlatformToken(token: String): Boolean {
        val t = token.trim()
        if (t.isBlank()) return false
        val ok = PlatformClient.verifyToken(t)
        prefs.platformToken = t
        prefs.platformAuthExpired = !ok
        prefs.platformError = if (ok) "" else "登录态无效或已过期"
        return ok
    }

    fun clearPlatformToken() {
        prefs.platformToken = ""
        prefs.platformError = ""
        prefs.platformAuthExpired = false
    }

    /** 单个模型的用量总量（用于诊断展示）。 */
    suspend fun modelBreakdown(): List<PlatformApi.ModelUsage> {
        val monthKey = YearMonth.now().toString()
        return db.platformDao().modelsOf(monthKey)
            .map { PlatformApi.ModelUsage(it.model, it.tokens, it.requests, it.cost) }
    }

    // ---------------------------------------------------------------- 本地记录

    /** 记录一次调用测试的精确用量（本地账本，作为平台数据之外的补充）。 */
    suspend fun recordChatUsage(usage: ChatUsage) {
        db.usageDao().insert(
            UsageRecord(
                epochDay = LocalDate.now().toEpochDay(),
                model = usage.model,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                cachedTokens = usage.cachedTokens,
                requests = 1,
                spent = (usage.promptTokens - usage.cachedTokens).coerceAtLeast(0) *
                    prefs.priceInput / 1_000_000.0 +
                    usage.completionTokens * prefs.priceOutput / 1_000_000.0,
                source = "chat",
                createdAt = System.currentTimeMillis()
            )
        )
        loadDashboard()
    }

    // ---------------------------------------------------------------- 刷新

    /**
     * 刷新：余额（API Key）+ 平台官方用量（登录态）。
     * 平台用量失败不影响余额展示，反之亦然。
     */
    suspend fun refresh(syncPlatform: Boolean = true, syncBalance: Boolean = true): SyncResult =
        syncMutex.withLock {
            val key = prefs.apiKey
            val token = prefs.platformToken
            if (key.isBlank() && token.isBlank()) return SyncResult.NoKey

            _refreshing.value = true
            try {
                var balanceInfo: BalanceInfo? = null
                var message = ""
                var platformOk = false

                if (syncBalance && key.isNotBlank()) {
                    try {
                        balanceInfo = DeepSeekClient.fetchBalance(prefs.baseUrl, key)
                        val now = System.currentTimeMillis()
                        db.balanceDao().insert(
                            BalanceSnapshot(
                                at = now,
                                currency = balanceInfo.currency,
                                balance = balanceInfo.totalValue,
                                isAvailable = balanceInfo.isAvailable
                            )
                        )
                        db.balanceDao().trim(now - 120L * 24 * 3600 * 1000)
                        prefs.lastUpdated = now
                        prefs.lastBalanceText = balanceInfo.totalBalance
                        prefs.lastCurrency = balanceInfo.currency
                        prefs.lastBalanceValue = balanceInfo.totalValue
                        prefs.lastGrantedBalance = balanceInfo.grantedBalance
                        prefs.lastToppedUpBalance = balanceInfo.toppedUpBalance
                        prefs.lastError = ""
                        Notifier.maybeNotifyLowBalance(context, balanceInfo)
                    } catch (t: Throwable) {
                        val msg = t.message ?: t.javaClass.simpleName
                        prefs.lastError = msg
                        message = "余额：$msg"
                    }
                }

                if (syncPlatform && token.isNotBlank()) {
                    platformOk = syncPlatformUsage(token).also { ok ->
                        if (!ok) message = listOf(message, prefs.platformError)
                            .filter { it.isNotBlank() }.joinToString("；")
                    }
                }

                loadDashboard()
                SyncResult.Ok(balanceInfo, 0L, platformOk, message)
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                prefs.lastError = msg
                loadDashboard()
                SyncResult.Failed(msg)
            } finally {
                _refreshing.value = false
            }
        }

    /** 拉取平台官方用量：上月累计 + 本月 + 近 14 天明细。 */
    private suspend fun syncPlatformUsage(token: String): Boolean {
        return try {
            val now = LocalDate.now()
            val month = YearMonth.from(now)
            val current = PlatformClient.fetchMonth(token, month.year, month.monthValue)

            val prevMonth = month.minusMonths(1)
            val previous = runCatching {
                PlatformClient.fetchMonth(token, prevMonth.year, prevMonth.monthValue)
            }.getOrNull()

            // 近 14 天明细（含今日，按 UTC 切分与平台一致）
            val exportStart = now.minusDays(13)
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            val exportEnd = now.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            val exportDays = runCatching {
                PlatformClient.fetchExportDays(token, exportStart, exportEnd)
            }.getOrNull().orEmpty()

            val stamp = System.currentTimeMillis()
            // 同一天可能同时出现在月度接口与导出接口里；导出窗口更贴近实时，优先采用它
            val allDays = LinkedHashMap<String, PlatformApi.Day>()
            current.days.forEach { allDays[it.date] = it }
            previous?.days?.forEach { allDays[it.date] = it }
            exportDays.forEach { allDays[it.date] = it }

            db.platformDao().upsertDays(
                allDays.values.map {
                    PlatformDay(
                        date = it.date,
                        tokens = it.tokens,
                        requests = it.requests,
                        cost = it.cost,
                        cacheHit = it.cacheHit,
                        cacheMiss = it.cacheMiss,
                        output = it.output,
                        updatedAt = stamp
                    )
                }
            )

            val monthKey = month.toString()
            db.platformDao().clearModels(monthKey)
            db.platformDao().upsertModels(
                current.models.map {
                    PlatformModel(
                        monthKey = monthKey,
                        model = it.model,
                        tokens = it.tokens,
                        requests = it.requests,
                        cost = it.cost,
                        updatedAt = stamp
                    )
                }
            )

            prefs.platformError = ""
            prefs.platformAuthExpired = false
            prefs.platformLastSync = stamp
            true
        } catch (e: PlatformClient.AuthExpiredException) {
            prefs.platformAuthExpired = true
            prefs.platformError = "登录状态已失效，请重新登录平台账号"
            false
        } catch (t: Throwable) {
            prefs.platformAuthExpired = false
            prefs.platformError = t.message ?: t.javaClass.simpleName
            false
        }
    }

    /**
     * 没有平台登录态时的兜底：把余额差额换算成 tokens 估算值。
     * 用专门存储，绝不与平台官方数据混在一起。
     */
    suspend fun storeEstimateFromBalanceDelta(delta: Double, now: Long): Long {
        val tokens = LedgerMath.estimateTokens(delta, prefs.priceInput, prefs.priceOutput)
        if (tokens <= 0) return 0L
        val (prompt, completion) = LedgerMath.splitEstimated(tokens)
        db.usageDao().insert(
            UsageRecord(
                epochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay(),
                model = "balance-estimate",
                promptTokens = prompt,
                completionTokens = completion,
                cachedTokens = 0,
                requests = 1,
                spent = delta,
                source = "estimated",
                createdAt = now
            )
        )
        return tokens
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
