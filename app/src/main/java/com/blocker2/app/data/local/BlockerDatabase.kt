package com.blocker2.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blocker2.app.data.local.dao.ActivityLogDao
import com.blocker2.app.data.local.dao.ActiveAppBlockDao
import com.blocker2.app.data.local.dao.MonitoredAppDao
import com.blocker2.app.data.local.dao.SiteRuleDao
import com.blocker2.app.data.local.dao.TelegramFilterRuleDao
import com.blocker2.app.data.local.dao.WordRuleDao
import com.blocker2.app.data.local.entity.ActivityLogEntity
import com.blocker2.app.data.local.entity.ActiveAppBlockEntity
import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.MonitoredAppEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import com.blocker2.app.data.local.entity.TelegramFilterRuleEntity
import com.blocker2.app.data.local.entity.WordRuleEntity

@Database(
    entities = [
        WordRuleEntity::class,
        TelegramFilterRuleEntity::class,
        SiteRuleEntity::class,
        AllowedSiteEntity::class,
        MonitoredAppEntity::class,
        ActivityLogEntity::class,
        ActiveAppBlockEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class BlockerDatabase : RoomDatabase() {
    abstract fun wordRuleDao(): WordRuleDao
    abstract fun telegramFilterRuleDao(): TelegramFilterRuleDao
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun activeAppBlockDao(): ActiveAppBlockDao

    companion object {
        private const val DATABASE_NAME = "blocker2_local.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS active_app_blocks (packageName TEXT NOT NULL PRIMARY KEY, sourceRuleId INTEGER, reason TEXT NOT NULL, createdAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_active_app_blocks_expiresAt ON active_app_blocks(expiresAt)")
            }
        }

        @Volatile
        private var instance: BlockerDatabase? = null

        fun getInstance(context: Context): BlockerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlockerDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
