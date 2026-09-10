package com.dsh.deepseekbalance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * tokens 用量记录。
 *
 * source 取值：
 *  - "chat"      App 内置调用测试获得的服务端精确 usage
 *  - "manual"    用户在「录入用量」中手动填写
 *  - "estimated" 由余额差额按官方价格倒推得到（仅参考）
 */
@Entity(tableName = "usage_record", indices = [Index("epochDay")])
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val requests: Int,
    val spent: Double,
    val source: String,
    val createdAt: Long
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

/** 余额采样点，用于展示趋势以及推算两次采样之间的消费。 */
@Entity(tableName = "balance_snapshot")
data class BalanceSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val currency: String,
    val balance: Double,
    val isAvailable: Boolean
)

/**
 * 平台官方用量（platform.deepseek.com 网页接口），按天缓存。
 * 这份数据与开放平台后台显示的数字完全一致，是本 App 的主口径。
 */
@Entity(tableName = "platform_day")
data class PlatformDay(
    @PrimaryKey val date: String,
    val tokens: Long,
    val requests: Long,
    val cost: Double,
    val cacheHit: Long,
    val cacheMiss: Long,
    val output: Long,
    val updatedAt: Long
)

/** 平台官方用量：按模型的当月汇总。 */
@Entity(tableName = "platform_model")
data class PlatformModel(
    @PrimaryKey val monthKey: String,
    val model: String,
    val tokens: Long,
    val requests: Long,
    val cost: Double,
    val updatedAt: Long
)
