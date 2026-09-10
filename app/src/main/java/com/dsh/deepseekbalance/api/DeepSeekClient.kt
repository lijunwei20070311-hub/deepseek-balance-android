package com.dsh.deepseekbalance.api

import com.dsh.deepseekbalance.prefs.DsbPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String, val httpCode: Int = 0) : Exception(message)

/**
 * DeepSeek 开放平台 API 客户端。
 *
 * - GET /user/balance  → 账户余额（含赠送/充值余额、可用状态）
 * - POST /chat/completions → 对话调用，返回服务端精确 token 用量
 */
object DeepSeekClient {

    private const val TIMEOUT_MS = 25_000

    suspend fun fetchBalance(baseUrl: String, apiKey: String): BalanceInfo =
        withContext(Dispatchers.IO) {
            ApiParsing.parseBalance(request("$baseUrl/user/balance", apiKey, null))
        }

    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): ChatResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", org.json.JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }
        val body = request("$baseUrl/chat/completions", apiKey, payload.toString())
        ApiParsing.parseChat(body, model)
    }

    private fun request(url: String, apiKey: String, jsonBody: String?): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = if (jsonBody == null) "GET" else "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (jsonBody != null) {
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: ""
            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { text.take(200) }
                throw ApiException(if (msg.isBlank()) "HTTP $code" else msg, code)
            }
            return text
        } finally {
            conn?.disconnect()
        }
    }

    fun defaultBase() = DsbPrefs.DEFAULT_BASE
}
