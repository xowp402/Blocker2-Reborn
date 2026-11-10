package com.blocker2.app.startup

import android.content.Context

/**
 * Stores only the user's explicit choice to restore the local DNS filter after reboot.
 * VPN consent is still checked independently by Android before the service is restarted.
 */
object StartupPreferences {
    private const val PREFERENCES_NAME = "blocker2_startup"
    private const val KEY_AUTO_START_SITE_FILTER = "auto_start_site_filter"

    fun isAutoStartSiteFilterEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START_SITE_FILTER, false)

    fun setAutoStartSiteFilter(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START_SITE_FILTER, enabled)
            .apply()
    }
}
