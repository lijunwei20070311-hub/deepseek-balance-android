package com.dsh.deepseekbalance.api

/**
 * DeepSeek /user/balance 返回的账户余额信息。
 * 官方文档：GET https://api.deepseek.com/user/balance
 */
data class BalanceInfo(
    val isAvailable: Boolean,
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String
) {
    val totalValue: Double
        get() = totalBalance.toDoubleOrNull() ?: 0.0

    val symbol: String
        get() = when (currency.uppercase()) {
            "USD" -> "$"
            "CNY" -> "¥"
            else -> ""
        }

    companion object {
        /** 余额展示文本，例如 ¥12.34 */
        fun format(currency: String, value: Double): String {
            val symbol = when (currency.uppercase()) {
                "USD" -> "$"
                "CNY" -> "¥"
                else -> "$currency "
            }
            return symbol + String.format(java.util.Locale.US, "%.2f", value)
        }
    }
}

/** 一次 chat/completions 调用的 token 用量（来自服务端 usage 字段，精确值）。 */
data class ChatUsage(
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val totalTokens: Long
)

data class ChatResult(
    val content: String,
    val usage: ChatUsage
)
