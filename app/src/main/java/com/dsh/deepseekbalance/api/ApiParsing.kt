package com.dsh.deepseekbalance.api

import org.json.JSONObject

/**
 * JSON → 模型映射。集中在这里，便于单元测试用真实响应样例验证。
 */
object ApiParsing {

    /** 解析 GET /user/balance 的响应。 */
    fun parseBalance(body: String): BalanceInfo {
        val json = JSONObject(body)
        val infos = json.optJSONArray("balance_infos")
        val first = if (infos != null && infos.length() > 0) {
            infos.optJSONObject(0) ?: JSONObject()
        } else {
            JSONObject()
        }
        return BalanceInfo(
            isAvailable = json.optBoolean("is_available", false),
            currency = first.optString("currency", "CNY"),
            totalBalance = first.optString("total_balance", "0"),
            grantedBalance = first.optString("granted_balance", "0"),
            toppedUpBalance = first.optString("topped_up_balance", "0")
        )
    }

    /** 解析 POST /chat/completions 的响应，取出文本与 usage。 */
    fun parseChat(body: String, fallbackModel: String): ChatResult {
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices")
        val content = if (choices != null && choices.length() > 0) {
            choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
        } else {
            ""
        }
        val usage = json.optJSONObject("usage")
        val promptTokens = usage?.optLong("prompt_tokens", 0L) ?: 0L
        val completionTokens = usage?.optLong("completion_tokens", 0L) ?: 0L
        val total = usage?.optLong("total_tokens", promptTokens + completionTokens)
            ?: (promptTokens + completionTokens)
        val details = usage?.optJSONObject("prompt_tokens_details")
        val cacheHit = when {
            usage?.has("prompt_cache_hit_tokens") == true ->
                usage.optLong("prompt_cache_hit_tokens", 0L)

            details?.has("cached_tokens") == true -> details.optLong("cached_tokens", 0L)
            else -> 0L
        }
        return ChatResult(
            content = content,
            usage = ChatUsage(
                model = json.optString("model", fallbackModel),
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                cachedTokens = cacheHit,
                totalTokens = total
            )
        )
    }
}
