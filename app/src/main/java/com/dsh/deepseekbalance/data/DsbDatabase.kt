package com.dsh.deepseekbalance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UsageRecord::class,
        BalanceSnapshot::class,
        PlatformDay::class,
        PlatformModel::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DsbDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao
    abstract fun balanceDao(): BalanceDao
    abstract fun platformDao(): PlatformDao

    companion object {
        @Volatile
        private var instance: DsbDatabase? = null

        /** v1 → v2：新增平台官方用量的两张表，保留已有本地账本数据。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `platform_day` (" +
                        "`date` TEXT NOT NULL, `tokens` INTEGER NOT NULL, " +
                        "`requests` INTEGER NOT NULL, `cost` REAL NOT NULL, " +
                        "`cacheHit` INTEGER NOT NULL, `cacheMiss` INTEGER NOT NULL, " +
                        "`output` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`date`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `platform_model` (" +
                        "`monthKey` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                        "`tokens` INTEGER NOT NULL, `requests` INTEGER NOT NULL, " +
                        "`cost` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`monthKey`, `model`))"
                )
            }
        }

        fun get(context: Context): DsbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DsbDatabase::class.java,
                    "deepseek_balance.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
