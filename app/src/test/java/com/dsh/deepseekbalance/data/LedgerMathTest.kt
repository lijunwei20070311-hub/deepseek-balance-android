package com.dsh.deepseekbalance.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerMathTest {

    @Test
    fun `blended price is the mean of input and output price`() {
        assertEquals(6.0, LedgerMath.blendedPricePerMillion(3.0, 9.0), 0.0001)
        assertEquals(5.0, LedgerMath.blendedPricePerMillion(1.0, 9.0), 0.0001)
    }

    /** 价格为 0 时按每百万 ¥0.01 兜底，避免换算出天文数字。 */
    @Test
    fun `zero prices fall back to the safety minimum`() {
        assertEquals(0.01, LedgerMath.blendedPricePerMillion(0.0, 0.0), 0.0000001)
    }

    /** ¥6 / 百万 tokens 的均价下，¥3 的消费约等于 50 万 tokens。 */
    @Test
    fun `estimates tokens from balance delta`() {
        val tokens = LedgerMath.estimateTokens(3.0, 3.0, 9.0)
        assertEquals(500_000L, tokens)
    }

    @Test
    fun `non positive delta yields zero`() {
        assertEquals(0L, LedgerMath.estimateTokens(0.0, 3.0, 9.0))
        assertEquals(0L, LedgerMath.estimateTokens(-5.0, 3.0, 9.0))
    }

    @Test
    fun `split keeps prompt and completion sum equal to total`() {
        val (prompt, completion) = LedgerMath.splitEstimated(400_000L)
        assertEquals(300_000L, prompt)
        assertEquals(100_000L, completion)
        assertEquals(400_000L, prompt + completion)
    }

    @Test
    fun `split of odd totals never exceeds total`() {
        val total = 100_003L
        val (prompt, completion) = LedgerMath.splitEstimated(total)
        assertEquals(total, prompt + completion)
    }
}
