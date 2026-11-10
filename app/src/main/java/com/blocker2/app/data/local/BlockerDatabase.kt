package com.blocker2.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blocker2.app.data.local.dao.ActivityLogDao
import com.blocker2.app.data.local.dao.MonitoredAppDao
import com.blocker2.app.data.local.dao.SiteRuleDao
import com.blocker2.app.data.local.dao.TelegramFilterRuleDao
import com.blocker2.app.data.local.dao.WordRuleDao
import com.blocker2.app.data.local.entity.ActivityLogEntity
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
        ActivityLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BlockerDatabase : RoomDatabase() {
    abstract fun wordRuleDao(): WordRuleDao
    abstract fun telegramFilterRuleDao(): TelegramFilterRuleDao
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        private const val DATABASE_NAME = "blocker2_local.db"

        @Volatile
        private var instance: BlockerDatabase? = null

        fun getInstance(context: Context): BlockerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlockerDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
