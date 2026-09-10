package com.dsh.deepseekbalance.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 数字与时间格式化工具。 */
object Fmt {

    /** 9,876 / 12.3 万 / 1.24 亿 */
    fun tokens(value: Long): String {
        val v = abs(value)
        return when {
            v < 10_000 -> String.format(Locale.US, "%,d", value)
            v < 100_000_000 -> trim(value / 10_000.0) + "万"
            else -> trim(value / 100_000_000.0) + "亿"
        }
    }

    private fun trim(value: Double): String {
        val s = String.format(Locale.US, "%.2f", value)
        return s.trimEnd('0').trimEnd('.')
    }

    fun money(currency: String, value: Double): String {
        val symbol = when (currency.uppercase()) {
            "USD" -> "$"
            "CNY" -> "¥"
            else -> "$currency "
        }
        val v = if (abs(value) >= 10_000) tokens(value.toLong()) else String.format(Locale.US, "%.2f", value)
        return symbol + v
    }

    fun time(ts: Long): String {
        if (ts <= 0) return "--"
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return fmt.format(Date(ts))
    }

    fun dayLabel(epochDay: Long): String {
        val date = java.time.LocalDate.ofEpochDay(epochDay)
        return "%d/%d".format(date.monthValue, date.dayOfMonth)
    }

    fun date(epochDay: Long): String =
        java.time.LocalDate.ofEpochDay(epochDay).toString()
}
