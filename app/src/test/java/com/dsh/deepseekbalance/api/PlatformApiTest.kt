package com.dsh.deepseekbalance.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformApiTest {

    /**
     * usage/amount 的真实结构（data.biz_data.{total,days}，类型名形如 REQUEST /
     * PROMPT_CACHE_HIT_TOKEN / PROMPT_CACHE_MISS_TOKEN / RESPONSE_TOKEN）。
     */
    private val amountBody = """
    {
      "code": 0,
      "msg": "success",
      "data": {
        "biz_code": 0,
        "biz_msg": "success",
        "biz_data": {
          "total": [
            {
              "model": "deepseek-v4-flash",
              "usage": [
                {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "800000"},
                {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "200000"},
                {"type": "RESPONSE_TOKEN", "amount": "150000"},
                {"type": "REQUEST", "amount": "42"}
              ]
            },
            {
              "model": "deepseek-v4-pro",
              "usage": [
                {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "100000"},
                {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "50000"},
                {"type": "RESPONSE_TOKEN", "amount": "30000"},
                {"type": "REQUEST", "amount": "5"}
              ]
            }
          ],
          "days": [
            {
              "date": "2026-09-09",
              "data": [
                {
                  "model": "deepseek-v4-flash",
                  "usage": [
                    {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "300000"},
                    {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "100000"},
                    {"type": "RESPONSE_TOKEN", "amount": "50000"},
                    {"type": "REQUEST", "amount": "20"}
                  ]
                }
              ]
            },
            {
              "date": "2026-09-10",
              "data": [
                {
                  "model": "deepseek-v4-flash",
                  "usage": [
                    {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "500000"},
                    {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "100000"},
                    {"type": "RESPONSE_TOKEN", "amount": "100000"},
                    {"type": "REQUEST", "amount": "22"}
                  ]
                }
              ]
            }
          ]
        }
      }
    }
    """.trimIndent()

    private val costBody = """
    {
      "code": 0,
      "data": {
        "biz_data": [
          {
            "currency": "USD",
            "total": [
              {
                "model": "deepseek-v4-flash",
                "usage": [
                  {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "0.01"},
                  {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "0.08"},
                  {"type": "RESPONSE_TOKEN", "amount": "0.16"}
                ]
              }
            ],
            "days": [
              {
                "date": "2026-09-10",
                "data": [
                  {
                    "model": "deepseek-v4-flash",
                    "usage": [
                      {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "0.01"},
                      {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "0.04"},
                      {"type": "RESPONSE_TOKEN", "amount": "0.09"}
                    ]
                  }
                ]
              }
            ]
          },
          {
            "currency": "CNY",
            "total": [
              {
                "model": "deepseek-v4-flash",
                "usage": [
                  {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "0.08"},
                  {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "0.60"},
                  {"type": "RESPONSE_TOKEN", "amount": "1.20"},
                  {"type": "REQUEST", "amount": "0"}
                ]
              }
            ],
            "days": [
              {
                "date": "2026-09-10",
                "data": [
                  {
                    "model": "deepseek-v4-flash",
                    "usage": [
                      {"type": "PROMPT_CACHE_HIT_TOKEN", "amount": "0.05"},
                      {"type": "PROMPT_CACHE_MISS_TOKEN", "amount": "0.30"},
                      {"type": "RESPONSE_TOKEN", "amount": "0.80"},
                      {"type": "REQUEST", "amount": "0"}
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

    @Test
    fun `parses monthly amount totals by model and type`() {
        val usage = PlatformApi.parseAmount(amountBody, "2026-09")
        println(
            "DBG2 monthTokens=${usage.monthTokens} req=${usage.monthRequests} hit=${usage.cacheHit} " +
                "miss=${usage.cacheMiss} out=${usage.outputTokens} models=${usage.models.map { it.model to it.tokens }}"
        )
        assertEquals(1_330_000L, usage.monthTokens) // 800k+200k+150k + 100k+50k+30k
        assertEquals(47L, usage.monthRequests)
        assertEquals(900_000L, usage.cacheHit)
        assertEquals(250_000L, usage.cacheMiss)
        assertEquals(180_000L, usage.outputTokens)
        assertEquals(1_150_000L, usage.inputTokens)
        assertEquals(2, usage.models.size)
        assertEquals("deepseek-v4-flash", usage.models[0].model)
        assertEquals(1_150_000L, usage.models[0].tokens)
        assertEquals(42L, usage.models[0].requests)
    }

    @Test
    fun `parses per day rows`() {
        val usage = PlatformApi.parseAmount(amountBody, "2026-09")
        assertEquals(2, usage.days.size)
        val day = usage.day("2026-09-10")!!
        assertEquals(700_000L, day.tokens)
        assertEquals(22L, day.requests)
        assertEquals(500_000L, day.cacheHit)
        assertEquals(100_000L, day.output)
    }

    @Test
    fun `cost response carries money amounts`() {
        val base = PlatformApi.parseAmount(amountBody, "2026-09")
        val withCost = PlatformApi.parseCost(costBody, base)
        assertEquals(1.88, withCost.monthCost, 0.0001)
        assertEquals("CNY", withCost.currency)
        assertEquals(1.15, withCost.day("2026-09-10")!!.cost, 0.0001)
        assertEquals(1.88, withCost.models.first { it.model == "deepseek-v4-flash" }.cost, 0.0001)
    }

    @Test
    fun `csv export rows are summed per day`() {
        val csv = """
            utc_date,model,api_key_name,type,amount,price
            2026-09-10,deepseek-v4-flash,my-key,PROMPT_CACHE_HIT_TOKEN,500000,0.0000001
            2026-09-10,deepseek-v4-flash,my-key,PROMPT_CACHE_MISS_TOKEN,100000,0.000003
            2026-09-10,deepseek-v4-flash,my-key,RESPONSE_TOKEN,100000,0.000008
            2026-09-10,deepseek-v4-flash,my-key,REQUEST,22,0
            2026-09-09,deepseek-v4-pro,other-key,RESPONSE_TOKEN,1000,0.00002
        """.trimIndent()
        val days = PlatformApi.parseAmountCsv(csv)
        assertEquals(2, days.size)
        val d = days.first { it.date == "2026-09-10" }
        assertEquals(700_000L, d.tokens)
        assertEquals(22L, d.requests)
        assertEquals(500_000L, d.cacheHit)
        assertEquals(100_000L, d.cacheMiss)
        assertEquals(100_000L, d.output)
        assertTrue(d.cost > 0.0)
    }

    @Test
    fun `detects expired platform session`() {
        assertTrue(PlatformApi.authExpired("""{"code":40003,"msg":"invalid token"}"""))
        assertTrue(PlatformApi.authExpired("""{"data":{"biz_code":40002}}"""))
        assertEquals(false, PlatformApi.authExpired("""{"code":0,"data":{}}"""))
    }

    @Test
    fun `merges exported days into monthly usage without losing totals`() {
        val base = PlatformApi.parseAmount(amountBody, "2026-09")
        val merged = base.mergeDays(
            listOf(
                PlatformApi.Day("2026-09-11", 12345, 3, 0.5, 10000, 2000, 345),
                PlatformApi.Day("2026-09-10", 999, 1, 0.1, 0, 0, 0)
            )
        )
        assertEquals(3, merged.days.size)
        // 导出窗口里的数据会覆盖（它更新），窗口外的月份数据保留
        assertEquals(999L, merged.day("2026-09-10")!!.tokens)
        assertEquals(450_000L, merged.day("2026-09-09")!!.tokens) // 300k+100k+50k
        assertEquals(12345L, merged.day("2026-09-11")!!.tokens)
        assertEquals(1_330_000L, merged.monthTokens)
    }
}
