package com.dsh.deepseekbalance.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * 平台网页版用量接口（platform.deepseek.com/api/v0）的模型与解析。
 *
 * 这些是开放平台网页后台使用的私有接口，官方公开 API 只有 /user/balance。
 * 之所以用它：API Key 只能查到余额，查不到「账号下全部 tokens 用量」；
 * 而这里返回的数字与平台后台页面完全一致。
 *
 * 接口：
 *  - GET /api/v0/usage/amount?month=&year=   本月用量（JSON，按天/模型/类型）
 *  - GET /api/v0/usage/cost?month=&year=     本月消费（JSON，单位为金额）
 *  - GET /api/v0/usage/export?start=&end=&tz=0  区间用量导出（zip 内含 CSV）
 *
 * 用量类型（type）：
 *  - REQUEST / request_count                 请求次数
 *  - PROMPT_CACHE_HIT_TOKEN / input_cache_hit_tokens   输入（缓存命中）
 *  - PROMPT_CACHE_MISS_TOKEN / input_cache_miss_tokens 输入（缓存未命中）
 *  - RESPONSE_TOKEN / output_tokens          输出
 */
object PlatformApi {

    const val BASE = "https://platform.deepseek.com"
    const val AMOUNT_URL = "$BASE/api/v0/usage/amount"
    const val COST_URL = "$BASE/api/v0/usage/cost"
    const val EXPORT_URL = "$BASE/api/v0/usage/export"
    const val SUMMARY_URL = "$BASE/api/v0/users/get_user_summary"

    /** 与网页后台一致的请求头，避免被网关当成异常客户端。 */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    const val REFERER = "$BASE/usage"

    /** 一天的用量。 */
    data class Day(
        val date: String,
        val tokens: Long,
        val requests: Long,
        val cost: Double,
        val cacheHit: Long,
        val cacheMiss: Long,
        val output: Long
    )

    /** 某个模型的用量合计。 */
    data class ModelUsage(
        val model: String,
        val tokens: Long,
        val requests: Long,
        val cost: Double
    )

    /** 一次完整的平台用量快照。 */
    data class Usage(
        val currency: String,
        val monthKey: String,
        val monthTokens: Long,
        val monthRequests: Long,
        val monthCost: Double,
        val cacheHit: Long,
        val cacheMiss: Long,
        val outputTokens: Long,
        val days: List<Day>,
        val models: List<ModelUsage>
    ) {
        val inputTokens: Long get() = cacheHit + cacheMiss

        fun day(date: String): Day? = days.firstOrNull { it.date == date }

        /** 导出接口拿到的按天数据合并进来（用于「今日」与历史累计）。 */
        fun mergeDays(extra: List<Day>, keepMonthTotals: Boolean = true): Usage {
            val map = LinkedHashMap<String, Day>()
            days.forEach { map[it.date] = it }
            extra.forEach { d ->
                val old = map[d.date]
                map[d.date] = if (old == null) {
                    d
                } else {
                    Day(
                        date = d.date,
                        tokens = if (d.tokens > 0) d.tokens else old.tokens,
                        requests = if (d.requests > 0) d.requests else old.requests,
                        cost = if (d.cost > 0) d.cost else old.cost,
                        cacheHit = if (d.cacheHit > 0) d.cacheHit else old.cacheHit,
                        cacheMiss = if (d.cacheMiss > 0) d.cacheMiss else old.cacheMiss,
                        output = if (d.output > 0) d.output else old.output
                    )
                }
            }
            return copy(days = map.values.sortedBy { it.date })
        }
    }

    sealed interface Result {
        data class Ok(val usage: Usage) : Result
        data class Failed(val message: String, val authExpired: Boolean = false) : Result
    }

    // ------------------------------------------------------------------ 解析

    /** 解析 usage/amount 的响应。 */
    fun parseAmount(body: String, monthKey: String): Usage {
        val root = JSONObject(body)
        val biz = bizBlocks(root).firstOrNull() ?: root.optJSONObject("biz_data") ?: JSONObject()

        var monthTokens = 0L
        var monthRequests = 0L
        var cacheHit = 0L
        var cacheMiss = 0L
        var output = 0L
        val modelTokens = LinkedHashMap<String, Long>()
        val modelRequests = LinkedHashMap<String, Long>()

        val total = biz.optJSONArray("total") ?: JSONArray()
        for (i in 0 until total.length()) {
            val item = total.optJSONObject(i) ?: continue
            val model = item.optString("model", "")
            var mt = 0L
            var mr = 0L
            val usage = item.optJSONArray("usage") ?: JSONArray()
            for (j in 0 until usage.length()) {
                val u = usage.optJSONObject(j) ?: continue
                val type = u.optString("type", "")
                val n = u.optString("amount", "0").toDoubleOrNull()?.toLong() ?: 0L
                when (normalizeType(type)) {
                    Kind.REQUEST -> {
                        monthRequests += n
                        mr += n
                    }

                    Kind.HIT -> {
                        monthTokens += n; cacheHit += n; mt += n
                    }

                    Kind.MISS -> {
                        monthTokens += n; cacheMiss += n; mt += n
                    }

                    Kind.OUTPUT -> {
                        monthTokens += n; output += n; mt += n
                    }

                    Kind.UNKNOWN -> Unit
                }
            }
            if (model.isNotBlank()) {
                modelTokens[model] = (modelTokens[model] ?: 0L) + mt
                modelRequests[model] = (modelRequests[model] ?: 0L) + mr
            }
        }

        val days = ArrayList<Day>()
        val bizDays = biz.optJSONArray("days") ?: JSONArray()
        for (i in 0 until bizDays.length()) {
            val d = bizDays.optJSONObject(i) ?: continue
            val date = d.optString("date", "")
            if (date.isBlank()) continue
            var tokens = 0L
            var requests = 0L
            var hit = 0L
            var miss = 0L
            var out = 0L
            val data = d.optJSONArray("data") ?: JSONArray()
            for (j in 0 until data.length()) {
                val m = data.optJSONObject(j) ?: continue
                val usage = m.optJSONArray("usage") ?: JSONArray()
                for (k in 0 until usage.length()) {
                    val u = usage.optJSONObject(k) ?: continue
                    val n = u.optString("amount", "0").toDoubleOrNull()?.toLong() ?: 0L
                    when (normalizeType(u.optString("type", ""))) {
                        Kind.REQUEST -> requests += n
                        Kind.HIT -> {
                            tokens += n; hit += n
                        }

                        Kind.MISS -> {
                            tokens += n; miss += n
                        }

                        Kind.OUTPUT -> {
                            tokens += n; out += n
                        }

                        Kind.UNKNOWN -> Unit
                    }
                }
            }
            days.add(Day(date, tokens, requests, 0.0, hit, miss, out))
        }

        val models = modelTokens.entries
            .map { ModelUsage(it.key, it.value, modelRequests[it.key] ?: 0L, 0.0) }
            .sortedByDescending { it.tokens }

        return Usage(
            currency = "CNY",
            monthKey = monthKey,
            monthTokens = monthTokens,
            monthRequests = monthRequests,
            monthCost = 0.0,
            cacheHit = cacheHit,
            cacheMiss = cacheMiss,
            outputTokens = output,
            days = days,
            models = models
        )
    }

    /** 解析 usage/cost 的响应：这里每个 usage.amount 就是金额。 */
    fun parseCost(body: String, base: Usage): Usage {
        val root = JSONObject(body)
        // 多币种时优先取 CNY（账户默认币种），否则取第一个
        val items = bizBlocks(root).filter { it.has("total") || it.has("days") }
        val item = items.firstOrNull { it.optString("currency", "").equals("CNY", true) }
            ?: items.firstOrNull()
            ?: JSONObject()

        val currency = item.optString("currency", base.currency).ifBlank { base.currency }

        var monthCost = 0.0
        val total = item.optJSONArray("total") ?: JSONArray()
        val modelCost = LinkedHashMap<String, Double>()
        for (i in 0 until total.length()) {
            val m = total.optJSONObject(i) ?: continue
            val model = m.optString("model", "")
            var c = 0.0
            val usage = m.optJSONArray("usage") ?: JSONArray()
            for (j in 0 until usage.length()) {
                val u = usage.optJSONObject(j) ?: continue
                if (normalizeType(u.optString("type", "")) == Kind.REQUEST) continue
                c += u.optDouble("amount", 0.0)
            }
            monthCost += c
            if (model.isNotBlank()) modelCost[model] = (modelCost[model] ?: 0.0) + c
        }

        val dayCost = LinkedHashMap<String, Double>()
        val days = item.optJSONArray("days") ?: JSONArray()
        for (i in 0 until days.length()) {
            val d = days.optJSONObject(i) ?: continue
            val date = d.optString("date", "")
            if (date.isBlank()) continue
            var c = 0.0
            val data = d.optJSONArray("data") ?: JSONArray()
            for (j in 0 until data.length()) {
                val m = data.optJSONObject(j) ?: continue
                val usage = m.optJSONArray("usage") ?: JSONArray()
                for (k in 0 until usage.length()) {
                    val u = usage.optJSONObject(k) ?: continue
                    if (normalizeType(u.optString("type", "")) == Kind.REQUEST) continue
                    c += u.optDouble("amount", 0.0)
                }
            }
            dayCost[date] = (dayCost[date] ?: 0.0) + c
        }

        return base.copy(
            currency = currency,
            monthCost = monthCost,
            days = base.days.map { it.copy(cost = dayCost[it.date] ?: it.cost) },
            models = base.models
                .map { it.copy(cost = modelCost[it.model] ?: it.cost) }
                .sortedByDescending { it.tokens }
        )
    }

    private enum class Kind { REQUEST, HIT, MISS, OUTPUT, UNKNOWN }

    /** usage/cost 的 biz_data 可能是对象，也可能是（多币种的）数组。 */
    private fun bizBlocks(root: JSONObject): List<JSONObject> {
        val data = root.optJSONObject("data") ?: root
        return when (val biz = data.opt("biz_data")) {
            is JSONArray -> (0 until biz.length()).mapNotNull { biz.optJSONObject(it) }
            is JSONObject -> listOf(biz)
            else -> emptyList()
        }
    }

    private fun normalizeType(raw: String): Kind = when (raw.uppercase()) {
        "REQUEST", "REQUEST_COUNT", "REQUESTS" -> Kind.REQUEST
        "PROMPT_CACHE_HIT_TOKEN", "INPUT_CACHE_HIT_TOKENS", "CACHE_HIT" -> Kind.HIT
        "PROMPT_CACHE_MISS_TOKEN", "INPUT_CACHE_MISS_TOKENS", "CACHE_MISS" -> Kind.MISS
        "RESPONSE_TOKEN", "OUTPUT_TOKENS" -> Kind.OUTPUT
        else -> Kind.UNKNOWN
    }

    // ------------------------------------------------------------------ CSV（export 用）

    /** 解析导出 zip 里的 amount CSV。 */
    fun parseAmountCsv(text: String): List<Day> {
        val lines = text.replace("\uFEFF", "").split('\n').map { it.trim('\r', ' ') }
            .filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val headers = splitCsvLine(lines[0])
        val idxDate = headers.indexOfFirst { it == "utc_date" || it == "date" }
        val idxType = headers.indexOfFirst { it == "type" }
        val idxAmount = headers.indexOfFirst { it == "amount" }
        val idxPrice = headers.indexOfFirst { it == "price" }
        if (idxDate < 0 || idxType < 0 || idxAmount < 0) return emptyList()

        val acc = LinkedHashMap<String, Day>()
        for (i in 1 until lines.size) {
            val cols = splitCsvLine(lines[i])
            if (cols.size <= maxOf(idxDate, idxType, idxAmount)) continue
            val date = normalizeDate(cols[idxDate])
            if (date.isBlank()) continue
            val amount = cols[idxAmount].toDoubleOrNull() ?: 0.0
            val price = if (idxPrice in cols.indices) cols[idxPrice].toDoubleOrNull() ?: 0.0 else 0.0
            val old = acc[date] ?: Day(date, 0, 0, 0.0, 0, 0, 0)
            acc[date] = when (normalizeType(cols[idxType])) {
                Kind.REQUEST -> old.copy(requests = old.requests + amount.toLong())
                Kind.HIT -> old.copy(
                    tokens = old.tokens + amount.toLong(),
                    cacheHit = old.cacheHit + amount.toLong(),
                    cost = old.cost + price * amount
                )

                Kind.MISS -> old.copy(
                    tokens = old.tokens + amount.toLong(),
                    cacheMiss = old.cacheMiss + amount.toLong(),
                    cost = old.cost + price * amount
                )

                Kind.OUTPUT -> old.copy(
                    tokens = old.tokens + amount.toLong(),
                    output = old.output + amount.toLong(),
                    cost = old.cost + price * amount
                )

                Kind.UNKNOWN -> old
            }
        }
        return acc.values.sortedBy { it.date }
    }

    internal fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"' && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"'); i++
                } else if (ch == '"') {
                    inQuotes = false
                } else {
                    sb.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        out.add(sb.toString()); sb.setLength(0)
                    }

                    else -> sb.append(ch)
                }
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    fun normalizeDate(v: String): String {
        val s = v.trim()
        if (s.length == 8 && !s.contains('-')) {
            return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8)
        }
        return s.take(10)
    }

    /** 判断响应是否表示登录态失效。 */
    fun authExpired(body: String): Boolean {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return false
        val code = root.optInt("code", 0)
        if (code == 40002 || code == 40003) return true
        val bizCode = root.optJSONObject("data")?.optInt("biz_code", 0) ?: 0
        return bizCode == 40002 || bizCode == 40003
    }
}
