package com.blocker2.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker2.app.Blocker2Application
import com.blocker2.app.core.text.RuleMatchMode
import com.blocker2.app.core.text.RuleMatcher
import com.blocker2.app.core.text.WordNormalizer
import com.blocker2.app.data.local.entity.ActiveAppBlockEntity
import com.blocker2.app.data.local.entity.ActivityLogEntity
import com.blocker2.app.data.local.entity.WordRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AccessibilityMonitorService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastMatchAt = ConcurrentHashMap<String, Long>()
    private val lastCommitmentEnforcementAt = ConcurrentHashMap<String, Long>()
    private var lastScanAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility monitoring connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastScanAt < MIN_SCAN_INTERVAL_MS) return
        lastScanAt = elapsed

        val root = rootInActiveWindow ?: return
        val visibleText = try {
            collectVisibleText(root)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not traverse active accessibility tree", error)
            return
        } finally {
            root.recycle()
        }
        if (visibleText.isBlank()) return

        serviceScope.launch {
            try { processSnapshot(packageName, visibleText) }
            catch (error: Exception) { Log.e(TAG, "Accessibility snapshot processing failed", error) }
        }
    }

    private suspend fun processSnapshot(packageName: String, visibleText: String) {
        val app = application as Blocker2Application
        val now = System.currentTimeMillis()
        val blockDao = app.database.activeAppBlockDao()
        blockDao.deleteExpired(now)

        if (blockDao.findActive(packageName, now) != null) {
            withContext(Dispatchers.Main) {
                if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                    Log.w(TAG, "Could not return home while enforcing timed block")
                }
            }
            return
        }

        val monitored = app.database.monitoredAppDao().observeMonitored().first()
            .firstOrNull { it.packageName == packageName } ?: return

        val commitment = app.database.commitmentSessionDao().getSession()
        if (commitment != null && commitment.expiresAt > now) {
            val previousEnforcement = lastCommitmentEnforcementAt[packageName] ?: 0L
            if (now - previousEnforcement >= COMMITMENT_ENFORCEMENT_COOLDOWN_MS) {
                lastCommitmentEnforcementAt[packageName] = now
                app.database.activityLogDao().insert(
                    ActivityLogEntity(
                        occurredAt = now,
                        sourcePackage = packageName,
                        sourceLabel = monitored.appName,
                        ruleType = "COMMITMENT",
                        matchedValue = "Personal commitment session",
                        reaction = "EXIT_ONLY",
                        durationMillis = (commitment.expiresAt - now).coerceAtLeast(0L),
                        outcome = "COMMITMENT_ENFORCED"
                    )
                )
                withContext(Dispatchers.Main) {
                    if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                        Log.w(TAG, "Android rejected HOME action during commitment mode")
                    }
                }
            }
            return
        }
        if (commitment != null && commitment.expiresAt <= now) {
            app.database.commitmentSessionDao().endSession()
        }
        val rules = app.wordRules.observeEnabled().first()
        var shouldExit = false
        var timedBlockCreated = false

        for (rule in rules) {
            if (!isRuleTargetedAt(rule, packageName)) continue
            val mode = runCatching { RuleMatchMode.valueOf(rule.matchMode) }
                .getOrDefault(RuleMatchMode.NORMALIZED_WORD)
            if (!RuleMatcher.matches(visibleText, rule.text, mode)) continue

            val matchKey = "$packageName:${rule.id}"
            val previous = lastMatchAt[matchKey] ?: 0L
            if (now - previous < MATCH_COOLDOWN_MS) continue
            lastMatchAt[matchKey] = now

            val reaction = rule.reaction.uppercase()
            val requiresExit = reaction == REACTION_WARN_AND_EXIT ||
                reaction == REACTION_EXIT_ONLY || reaction == REACTION_TEMPORARY_BLOCK
            if (requiresExit) shouldExit = true

            val startsTimedBlock = !timedBlockCreated &&
                (reaction == REACTION_WARN_AND_EXIT || reaction == REACTION_TEMPORARY_BLOCK) &&
                rule.blockDurationMillis > 0L
            if (startsTimedBlock) {
                blockDao.upsert(
                    ActiveAppBlockEntity(
                        packageName = packageName,
                        sourceRuleId = rule.id,
                        reason = rule.customWarning?.takeIf { it.isNotBlank() } ?: rule.text,
                        createdAt = now,
                        expiresAt = now + rule.blockDurationMillis
                    )
                )
                timedBlockCreated = true
                BlockNotificationManager.showTimedBlock(
                    context = this@AccessibilityMonitorService,
                    packageName = packageName,
                    expiresAt = now + rule.blockDurationMillis,
                    currentTimeMillis = now
                )
            }

            if (rule.statisticsEnabled) app.wordRules.recordDetection(rule.id)
            app.database.monitoredAppDao().incrementDetectionCount(packageName, now)
            app.database.activityLogDao().insert(
                ActivityLogEntity(
                    occurredAt = now,
                    sourcePackage = packageName,
                    sourceLabel = monitored.appName,
                    ruleType = "WORD",
                    ruleId = rule.id,
                    matchedValue = rule.text,
                    reaction = rule.reaction,
                    durationMillis = if (startsTimedBlock) rule.blockDurationMillis else 0L,
                    outcome = when {
                        reaction == REACTION_LOG_ONLY -> "DETECTED"
                        reaction == REACTION_EXIT_ONLY -> "EXIT_REQUESTED"
                        startsTimedBlock -> "TEMPORARY_BLOCK_STARTED"
                        requiresExit -> "EXIT_REQUESTED"
                        else -> "DETECTED"
                    }
                )
            )
            Log.i(TAG, "Word rule detected: ruleId=${rule.id}, package=$packageName")
        }

        if (isTelegramPackage(packageName)) {
            val normalizedSnapshot = WordNormalizer.normalize(visibleText)
            val telegramRules = app.telegramRules.observeEnabled().first()
            for (rule in telegramRules) {
                val normalizedRule = rule.normalizedValue
                val matched = when (rule.ruleType.uppercase()) {
                    "WORD" -> RuleMatcher.matches(visibleText, rule.value, RuleMatchMode.NORMALIZED_WORD)
                    "PHRASE" -> normalizedSnapshot.contains(normalizedRule)
                    "CHANNEL_URL", "CHANNEL_ID", "USERNAME" ->
                        normalizedRule.isNotBlank() && normalizedSnapshot.contains(normalizedRule)
                    else -> false
                }
                if (!matched) continue

                val matchKey = packageName + ":telegram:" + rule.id
                val previous = lastMatchAt[matchKey] ?: 0L
                if (now - previous < MATCH_COOLDOWN_MS) continue
                lastMatchAt[matchKey] = now

                val reaction = rule.reaction.uppercase()
                val requiresExit = reaction == REACTION_WARN_AND_EXIT ||
                    reaction == REACTION_EXIT_ONLY || reaction == REACTION_TEMPORARY_BLOCK
                if (requiresExit) shouldExit = true

                val startsTimedBlock = !timedBlockCreated &&
                    (reaction == REACTION_WARN_AND_EXIT || reaction == REACTION_TEMPORARY_BLOCK) &&
                    rule.blockDurationMillis > 0L
                if (startsTimedBlock) {
                    blockDao.upsert(
                        ActiveAppBlockEntity(
                            packageName = packageName,
                            sourceRuleId = rule.id,
                            reason = rule.value,
                            createdAt = now,
                            expiresAt = now + rule.blockDurationMillis
                        )
                    )
                    timedBlockCreated = true
                    BlockNotificationManager.showTimedBlock(
                        context = this@AccessibilityMonitorService,
                        packageName = packageName,
                        expiresAt = now + rule.blockDurationMillis,
                        currentTimeMillis = now
                    )
                }

                app.telegramRules.recordDetection(rule.id)
                app.database.monitoredAppDao().incrementDetectionCount(packageName, now)
                app.database.activityLogDao().insert(
                    ActivityLogEntity(
                        occurredAt = now,
                        sourcePackage = packageName,
                        sourceLabel = monitored.appName,
                        ruleType = "TELEGRAM",
                        ruleId = rule.id,
                        matchedValue = rule.value,
                        reaction = rule.reaction,
                        durationMillis = if (startsTimedBlock) rule.blockDurationMillis else 0L,
                        outcome = when {
                            reaction == REACTION_LOG_ONLY -> "DETECTED"
                            reaction == REACTION_EXIT_ONLY -> "EXIT_REQUESTED"
                            startsTimedBlock -> "TEMPORARY_BLOCK_STARTED"
                            requiresExit -> "EXIT_REQUESTED"
                            else -> "DETECTED"
                        }
                    )
                )
                Log.i(TAG, "Telegram rule detected: ruleId=" + rule.id + ", package=" + packageName)
            }
        }

        if (shouldExit) {
            withContext(Dispatchers.Main) {
                if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                    Log.w(TAG, "Android rejected the HOME global action")
                }
            }
        }
    }

    private fun isTelegramPackage(packageName: String): Boolean =
        packageName == "org.telegram.messenger" ||
            packageName == "org.telegram.messenger.beta" ||
            packageName == "org.telegram.plus" ||
            packageName == "org.thunderdog.challegram" ||
            packageName == "nekox.messenger"

    private fun isRuleTargetedAt(rule: WordRuleEntity, packageName: String): Boolean {
        val configured = rule.targetPackages.split("|").map(String::trim).filter(String::isNotEmpty)
        return configured.isEmpty() || packageName in configured
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): String {
        val result = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES && result.length < MAX_TEXT_LENGTH) {
            val node = stack.removeLast()
            visited++
            try {
                if (!node.isPassword) {
                    node.text?.toString()?.takeIf(String::isNotBlank)?.let { result.append(it).append(" ") }
                    node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let { result.append(it).append(" ") }
                }
                for (index in 0 until node.childCount) {
                    if (visited + stack.size >= MAX_NODES) break
                    val child = node.getChild(index) ?: continue
                    stack.add(child)
                }
            } catch (error: RuntimeException) {
                Log.d(TAG, "Skipped an inaccessible accessibility node", error)
            } finally {
                if (node !== root) node.recycle()
            }
        }
        return result.toString().take(MAX_TEXT_LENGTH)
    }

    override fun onInterrupt() { Log.w(TAG, "Accessibility monitoring interrupted by the system") }

    override fun onDestroy() {
        serviceScope.cancel()
        lastMatchAt.clear()
        lastCommitmentEnforcementAt.clear()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Blocker2.Accessibility"
        private const val MIN_SCAN_INTERVAL_MS = 350L
        private const val MATCH_COOLDOWN_MS = 2_000L
        private const val COMMITMENT_ENFORCEMENT_COOLDOWN_MS = 5_000L
        private const val MAX_NODES = 500
        private const val MAX_TEXT_LENGTH = 12_000
        private const val REACTION_WARN_AND_EXIT = "WARN_AND_EXIT"
        private const val REACTION_EXIT_ONLY = "EXIT_ONLY"
        private const val REACTION_TEMPORARY_BLOCK = "TEMPORARY_BLOCK"
        private const val REACTION_LOG_ONLY = "LOG_ONLY"
    }
}
