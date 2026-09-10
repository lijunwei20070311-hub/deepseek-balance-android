package com.dsh.deepseekbalance.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * platform.deepseek.com 网页接口客户端：用登录态 userToken 拉取账号下的
 * 全量 tokens 用量（今日 / 本月 / 累计 / 分模型 / 消费）。
 */
object PlatformClient {

    private const val TIMEOUT_MS = 25_000
    private const val MAX_BYTES = 32 * 1024 * 1024

    class AuthExpiredException : Exception("登录状态已失效，请重新登录")

    /** 拉取某月用量（amount + cost 合并）。 */
    suspend fun fetchMonth(token: String, year: Int, month: Int): PlatformApi.Usage =
        withContext(Dispatchers.IO) {
            val monthKey = "%04d-%02d".format(year, month)
            val amountBody = get(
                "${PlatformApi.AMOUNT_URL}?month=$month&year=$year",
                token,
                "application/json"
            )
            val base = PlatformApi.parseAmount(amountBody, monthKey)
            val costBody = runCatching {
                get("${PlatformApi.COST_URL}?month=$month&year=$year", token, "application/json")
            }.getOrNull()
            if (costBody == null) base else PlatformApi.parseCost(costBody, base)
        }

    /**
     * 导出指定区间的按天用量（服务端返回 zip，内含 amount CSV）。
     * start/end 为秒级时间戳，tz=0 与网页后台保持一致。
     */
    suspend fun fetchExportDays(token: String, startSec: Long, endSec: Long): List<PlatformApi.Day> =
        withContext(Dispatchers.IO) {
            val bytes = getBytes(
                "${PlatformApi.EXPORT_URL}?start=$startSec&end=$endSec&tz=0",
                token
            )
            val csv = extractAmountCsv(bytes)
                ?: throw IllegalStateException("导出内容里没有找到 amount CSV")
            PlatformApi.parseAmountCsv(csv)
        }

    /** 轻量校验登录态是否可用。 */
    suspend fun verifyToken(token: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val now = java.util.Calendar.getInstance()
            val year = now.get(java.util.Calendar.YEAR)
            val month = now.get(java.util.Calendar.MONTH) + 1
            get("${PlatformApi.AMOUNT_URL}?month=$month&year=$year", token, "application/json")
            true
        }.getOrElse { false }
    }

    // ---------------------------------------------------------------- 内部实现

    private fun extractAmountCsv(bytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var best: String? = null
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.endsWith(".csv")) {
                    val text = BufferedReader(InputStreamReader(zip, Charsets.UTF_8)).readText()
                    if (best == null) best = text
                    if (name.contains("amount")) return text
                }
                entry = zip.nextEntry
            }
            return best
        }
    }

    private fun headers(conn: HttpURLConnection, token: String) {
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
        conn.setRequestProperty("User-Agent", PlatformApi.USER_AGENT)
        conn.setRequestProperty("Referer", PlatformApi.REFERER)
        conn.setRequestProperty("Origin", PlatformApi.BASE)
    }

    private fun get(url: String, token: String, accept: String): String {
        val bytes = getBytes(url, token, accept)
        return String(bytes, Charsets.UTF_8)
    }

    private fun getBytes(
        url: String,
        token: String,
        accept: String = "application/json, text/plain, */*"
    ): ByteArray {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", accept)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", PlatformApi.USER_AGENT)
                setRequestProperty("Referer", PlatformApi.REFERER)
                setRequestProperty("Origin", PlatformApi.BASE)
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val out = ByteArrayOutputStream()
            if (stream != null) {
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_BYTES) break
                    out.write(buf, 0, n)
                }
            }
            val bytes = out.toByteArray()
            if (code == 401 || code == 403) throw AuthExpiredException()
            if (code !in 200..299) {
                val text = String(bytes, Charsets.UTF_8)
                if (PlatformApi.authExpired(text)) throw AuthExpiredException()
                throw IllegalStateException("HTTP $code ${text.take(160)}")
            }
            if (bytes.isNotEmpty()) {
                val head = String(bytes, 0, minOf(64, bytes.size), Charsets.UTF_8)
                if (head.trimStart().startsWith("{")) {
                    val text = String(bytes, Charsets.UTF_8)
                    if (PlatformApi.authExpired(text)) throw AuthExpiredException()
                }
            }
            return bytes
        } finally {
            conn?.disconnect()
        }
    }
}
