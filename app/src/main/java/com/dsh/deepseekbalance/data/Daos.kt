package com.dsh.deepseekbalance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class Totals(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cachedTokens: Long = 0,
    val requests: Long = 0,
    val spent: Double = 0.0
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

data class DayAgg(
    val epochDay: Long,
    val promptTokens: Long,
    val completionTokens: Long
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

@Dao
interface UsageDao {

    @Insert
    suspend fun insert(record: UsageRecord): Long

    @Query("DELETE FROM usage_record WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM usage_record WHERE source = 'estimated' AND epochDay = :epochDay")
    suspend fun deleteEstimatedOn(epochDay: Long)

    @Query(
        """
        SELECT IFNULL(SUM(promptTokens),0) AS promptTokens,
               IFNULL(SUM(completionTokens),0) AS completionTokens,
               IFNULL(SUM(cachedTokens),0) AS cachedTokens,
               IFNULL(SUM(requests),0) AS requests,
               IFNULL(SUM(spent),0) AS spent
        FROM usage_record
        WHERE epochDay >= :fromDay AND epochDay <= :toDay
        """
    )
    suspend fun totalsBetween(fromDay: Long, toDay: Long): Totals

    @Query(
        """
        SELECT IFNULL(SUM(promptTokens),0) AS promptTokens,
               IFNULL(SUM(completionTokens),0) AS completionTokens,
               IFNULL(SUM(cachedTokens),0) AS cachedTokens,
               IFNULL(SUM(requests),0) AS requests,
               IFNULL(SUM(spent),0) AS spent
        FROM usage_record
        """
    )
    suspend fun totalsAll(): Totals

    @Query(
        """
        SELECT epochDay,
               IFNULL(SUM(promptTokens),0) AS promptTokens,
               IFNULL(SUM(completionTokens),0) AS completionTokens
        FROM usage_record
        WHERE epochDay >= :fromDay
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """
    )
    suspend fun dailyFrom(fromDay: Long): List<DayAgg>

    @Query("SELECT * FROM usage_record ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<UsageRecord>

    @Query("SELECT COUNT(*) FROM usage_record WHERE source = 'estimated'")
    suspend fun estimatedCount(): Int
}

@Dao
interface BalanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: BalanceSnapshot): Long

    @Query("SELECT * FROM balance_snapshot ORDER BY at DESC LIMIT 1")
    suspend fun latest(): BalanceSnapshot?

    @Query("DELETE FROM balance_snapshot WHERE at < :before")
    suspend fun trim(before: Long)
}

/** 平台官方用量（与开放平台后台一致的口径）。 */
@Dao
interface PlatformDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDays(days: List<PlatformDay>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<PlatformModel>)

    @Query("SELECT * FROM platform_day ORDER BY date ASC")
    suspend fun allDays(): List<PlatformDay>

    @Query("SELECT * FROM platform_day WHERE date >= :fromDate ORDER BY date ASC")
    suspend fun daysFrom(fromDate: String): List<PlatformDay>

    @Query("SELECT * FROM platform_day WHERE date = :date LIMIT 1")
    suspend fun day(date: String): PlatformDay?

    @Query("SELECT * FROM platform_model WHERE monthKey = :monthKey ORDER BY tokens DESC")
    suspend fun modelsOf(monthKey: String): List<PlatformModel>

    @Query("DELETE FROM platform_model WHERE monthKey = :monthKey")
    suspend fun clearModels(monthKey: String)

    @Query("SELECT MAX(updatedAt) FROM platform_day")
    suspend fun lastUpdated(): Long?
}
