package com.blocker2.app.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import com.blocker2.app.Blocker2Application
import com.blocker2.app.accessibility.BlockNotificationManager
import com.blocker2.app.vpn.LocalDnsVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores persisted, time-bounded state after Android has completed booting.
 *
 * Accessibility monitoring itself is managed by Android and is not force-started here.
 * The DNS VPN is restarted only when the user previously opted in and Android still
 * grants the VPN consent.
 */
class BootRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val app = appContext as Blocker2Application
                val now = System.currentTimeMillis()
                val activeBlocks = app.database.activeAppBlockDao()
                    .observeActive(now)
                    .first()

                activeBlocks.forEach { block ->
                    BlockNotificationManager.showTimedBlock(
                        context = appContext,
                        packageName = block.packageName,
                        expiresAt = block.expiresAt,
                        currentTimeMillis = now
                    )
                }

                if (StartupPreferences.isAutoStartSiteFilterEnabled(appContext) &&
                    VpnService.prepare(appContext) == null
                ) {
                    val serviceIntent = Intent(appContext, LocalDnsVpnService::class.java)
                        .setAction(LocalDnsVpnService.ACTION_START)
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not restore Blocker2 state after boot", error)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private const val TAG = "Blocker2.BootRestore"
    }
}
