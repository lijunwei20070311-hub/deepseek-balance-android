package com.dsh.deepseekbalance.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiParsingTest {

    /** 官方文档中的响应样例（含 CNY 与 USD 两种币种）。 */
    private val balanceBody = """
        {
          "is_available": true,
          "balance_infos": [
            {
              "currency": "CNY",
              "total_balance": "110.00",
              "granted_balance": "10.00",
              "topped_up_balance": "100.00"
            }
          ]
        }
    """.trimIndent()

    private val balanceUsdBody = """
        {
          "is_available": false,
          "balance_infos": [
            {
              "currency": "USD",
              "total_balance": "0.05",
              "granted_balance": "0.00",
              "topped_up_balance": "0.05"
            }
          ]
        }
    """.trimIndent()

    private val chatBody = """
        {
          "id": "chat-1",
          "model": "deepseek-v4-flash",
          "choices": [
            { "index": 0, "message": { "role": "assistant", "content": "你好！" }, "finish_reason": "stop" }
          ],
          "usage": {
            "prompt_tokens": 1200,
            "completion_tokens": 300,
            "total_tokens": 1500,
            "prompt_cache_hit_tokens": 800,
            "prompt_cache_miss_tokens": 400
          }
        }
    """.trimIndent()

    @Test
    fun `parses balance response`() {
        val info = ApiParsing.parseBalance(balanceBody)
        assertTrue(info.isAvailable)
        assertEquals("CNY", info.currency)
        assertEquals(110.0, info.totalValue, 0.0001)
        assertEquals("10.00", info.grantedBalance)
        assertEquals("100.00", info.toppedUpBalance)
        assertEquals("¥110.00", BalanceInfo.format(info.currency, info.totalValue))
    }

    @Test
    fun `parses unavailable usd balance`() {
        val info = ApiParsing.parseBalance(balanceUsdBody)
        assertFalse(info.isAvailable)
        assertEquals("USD", info.currency)
        assertEquals("\u0024" + "0.05", BalanceInfo.format(info.currency, info.totalValue))
    }

    @Test
    fun `parses chat usage including cache hits`() {
        val result = ApiParsing.parseChat(chatBody, "fallback-model")
        assertEquals("你好！", result.content)
        assertEquals("deepseek-v4-flash", result.usage.model)
        assertEquals(1200L, result.usage.promptTokens)
        assertEquals(300L, result.usage.completionTokens)
        assertEquals(800L, result.usage.cachedTokens)
        assertEquals(1500L, result.usage.totalTokens)
    }

    @Test
    fun `handles missing usage fields gracefully`() {
        val body = """
            {"model":"m","choices":[{"message":{"content":"hi"}}]}
        """.trimIndent()
        val result = ApiParsing.parseChat(body, "fallback-model")
        assertEquals("hi", result.content)
        assertEquals(0L, result.usage.totalTokens)
        assertEquals("m", result.usage.model)
    }
}
