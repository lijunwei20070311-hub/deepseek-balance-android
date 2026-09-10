package com.dsh.deepseekbalance.data

/**
 * 余额差额 → tokens 的换算规则（纯函数，便于单元测试）。
 *
 * 平台只提供余额接口，没有历史用量接口，因此这里用「余额差额 ÷ 价格」估算
 * tokens 数量，作为参考值展示（界面会标记为「估算」）。
 */
object LedgerMath {

    /** 输入/输出 tokens 的拆分比例（经验值：输入约占 3/4）。 */
    private const val PROMPT_SHARE = 3

    /** 价格下限，避免用户把价格填成 0 时换算出天文数字。 */
    private const val MIN_PRICE = 0.01

    fun blendedPricePerMillion(priceInput: Double, priceOutput: Double): Double =
        (priceInput.coerceAtLeast(MIN_PRICE) + priceOutput.coerceAtLeast(MIN_PRICE)) / 2.0

    /** 由消费金额估算 tokens 总数。 */
    fun estimateTokens(delta: Double, priceInput: Double, priceOutput: Double): Long {
        if (delta <= 0.0) return 0L
        val perMillion = blendedPricePerMillion(priceInput, priceOutput)
        return (delta / perMillion * 1_000_000.0).toLong()
    }

    /** 按 3:1 拆分估算出的 tokens，保证两部分之和等于总数。 */
    fun splitEstimated(totalTokens: Long): Pair<Long, Long> {
        if (totalTokens <= 0L) return 0L to 0L
        val completion = totalTokens * (4 - PROMPT_SHARE) / 4
        return (totalTokens - completion) to completion
    }
}
