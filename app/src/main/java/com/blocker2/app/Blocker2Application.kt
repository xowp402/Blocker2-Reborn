package com.blocker2.app

import android.app.Application
import com.blocker2.app.data.local.BlockerDatabase
import com.blocker2.app.data.repository.SiteRuleRepository
import com.blocker2.app.data.repository.TelegramRuleRepository
import com.blocker2.app.data.repository.WordRuleRepository

/**
 * Application-scoped dependency container.
 *
 * The database is created lazily and uses application context, so Activity recreation
 * cannot create extra database instances or retain an Activity reference.
 */
class Blocker2Application : Application() {

    val database: BlockerDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BlockerDatabase.getInstance(this)
    }

    val wordRules: WordRuleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WordRuleRepository(database.wordRuleDao())
    }

    val telegramRules: TelegramRuleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TelegramRuleRepository(database.telegramFilterRuleDao())
    }

    val siteRules: SiteRuleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SiteRuleRepository(database.siteRuleDao())
    }
}
