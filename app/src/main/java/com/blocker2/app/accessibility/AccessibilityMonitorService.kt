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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccessibilityMonitorService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val matchCooldown = CooldownGate()
    private val commitmentCooldown = CooldownGate()
    private val scanThrottle = ScanThrottle()
    private val processingGate = ScanProcessingGate()

    override fun onServiceConnected() {
        super.onServiceConnected()
        _connectionState.value = true
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
        if (!scanThrottle.tryAcquire(elapsed, MIN_SCAN_INTERVAL_MS)) return

        if (!processingGate.tryAcquire()) return

        var handedOffToCoroutine = false
        try {
            val root = try {
                rootInActiveWindow
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not obtain active accessibility window", error)
                null
            }
            val visibleText = if (root == null) {
                ""
            } else {
                try {
                    val activePackage = root.packageName?.toString()
                    if (!SnapshotPackageGuard.belongsToEvent(packageName, activePackage)) {
                        Log.d(TAG, "Discarding stale accessibility tree for $activePackage; event belongs to $packageName")
                        return
                    }
                    collectVisibleText(root)
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Could not traverse active accessibility tree", error)
                    ""
                } finally {
                    runCatching { root.recycle() }
                }
            }

            val processingJob = serviceScope.launch {
                try {
                    processSnapshot(packageName, visibleText)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Accessibility snapshot processing failed", error)
                }
            }
            // Transfer ownership before registering: an already-cancelled job may
            // invoke its completion handler immediately.
            handedOffToCoroutine = true
            processingJob.invokeOnCompletion { cause ->
                if (cause != null && cause !is CancellationException) {
                    Log.w(TAG, "Accessibility snapshot job completed exceptionally", cause)
                }
                processingGate.release()
            }
        } finally {
            if (!handedOffToCoroutine) processingGate.release()
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
            // Enforce the commitment before attempting any best-effort analytics write.
            // A full disk or database error must never prevent the HOME action.
            withContext(Dispatchers.Main) {
                if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                    Log.w(TAG, "Android rejected HOME action during commitment mode")
                }
            }

            // Cooldown limits duplicate log entries, never enforcement itself.
            if (commitmentCooldown.tryAcquire(packageName, now, COMMITMENT_ENFORCEMENT_COOLDOWN_MS)) {
                try {
                    app.database.activityLogDao().insert(
                        ActivityLogEntity(
                            occurredAt = now,
                            sourcePackage = packageName,
                            sourceLabel = monitored.appName,
                            ruleType = "COMMITMENT",
                            matchedValue = "Personal commitment session",
                            reaction = "EXIT_ONLY",
                            durationMillis = TimedBlockTime.remainingMillis(commitment.expiresAt, now),
                            outcome = "COMMITMENT_ENFORCED"
                        )
                    )
                } catch (error: Exception) {
                    Log.w(TAG, "Could not record commitment enforcement; restriction was already applied", error)
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
            if (!matchCooldown.tryAcquire(matchKey, now, MATCH_COOLDOWN_MS)) continue
            val reactionDecision = RuleReactionPolicy.evaluate(
                reaction = rule.reaction,
                durationMillis = rule.blockDurationMillis,
                timedBlockAlreadyCreated = timedBlockCreated
            )
            if (reactionDecision.requiresExit) shouldExit = true

            val startsTimedBlock = reactionDecision.startsTimedBlock
            if (startsTimedBlock) {
                val expiresAt = TimedBlockExpiry.calculate(now, rule.blockDurationMillis)
                blockDao.upsert(
                    ActiveAppBlockEntity(
                        packageName = packageName,
                        sourceRuleId = rule.id,
                        reason = rule.customWarning?.takeIf { it.isNotBlank() } ?: rule.text,
                        createdAt = now,
                        expiresAt = expiresAt
                    )
                )
                timedBlockCreated = true
                BlockNotificationManager.showTimedBlock(
                    context = this@AccessibilityMonitorService,
                    packageName = packageName,
                    expiresAt = expiresAt,
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
                    outcome = reactionDecision.outcome
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
                if (!matchCooldown.tryAcquire(matchKey, now, MATCH_COOLDOWN_MS)) continue
                val reactionDecision = RuleReactionPolicy.evaluate(
                    reaction = rule.reaction,
                    durationMillis = rule.blockDurationMillis,
                    timedBlockAlreadyCreated = timedBlockCreated
                )
                if (reactionDecision.requiresExit) shouldExit = true

                val startsTimedBlock = reactionDecision.startsTimedBlock
                if (startsTimedBlock) {
                    val expiresAt = TimedBlockExpiry.calculate(now, rule.blockDurationMillis)
                    blockDao.upsert(
                        ActiveAppBlockEntity(
                            packageName = packageName,
                            sourceRuleId = rule.id,
                            reason = rule.value,
                            createdAt = now,
                            expiresAt = expiresAt
                        )
                    )
                    timedBlockCreated = true
                    BlockNotificationManager.showTimedBlock(
                        context = this@AccessibilityMonitorService,
                        packageName = packageName,
                        expiresAt = expiresAt,
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
                        outcome = reactionDecision.outcome
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

    private fun isRuleTargetedAt(rule: WordRuleEntity, packageName: String): Boolean =
        PackageTargetMatcher.matches(rule.targetPackages, packageName)

    private fun collectVisibleText(root: AccessibilityNodeInfo): String {
        val result = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        try {
            while (stack.isNotEmpty() && visited < MAX_NODES && result.length < MAX_TEXT_LENGTH) {
                val node = stack.removeLast()
                visited++
                try {
                    if (!node.isPassword) {
                        appendLimited(result, node.text)
                        appendLimited(result, node.contentDescription)
                    }
                    for (index in 0 until node.childCount) {
                        if (result.length >= MAX_TEXT_LENGTH || visited + stack.size >= MAX_NODES) break
                        val child = node.getChild(index) ?: continue
                        stack.add(child)
                    }
                } catch (error: RuntimeException) {
                    Log.d(TAG, "Skipped an inaccessible accessibility node", error)
                } finally {
                    if (node !== root) runCatching { node.recycle() }
                }
            }
        } finally {
            // Recycle children that were queued but not visited because a traversal
            // limit was reached or an unexpected error interrupted the scan.
            while (stack.isNotEmpty()) {
                val pending = stack.removeLast()
                if (pending !== root) runCatching { pending.recycle() }
            }
        }
        return result.toString().take(MAX_TEXT_LENGTH)
    }

    private fun appendLimited(target: StringBuilder, value: CharSequence?) {
        if (value.isNullOrBlank() || target.length >= MAX_TEXT_LENGTH) return
        val remaining = MAX_TEXT_LENGTH - target.length
        val charsToAppend = minOf(value.length, remaining)
        target.append(value, 0, charsToAppend)
        if (target.length < MAX_TEXT_LENGTH) target.append(' ')
    }

    override fun onInterrupt() {
        _connectionState.value = false
        Log.w(TAG, "Accessibility monitoring interrupted by the system")
    }

    override fun onDestroy() {
        _connectionState.value = false
        serviceScope.cancel()
        matchCooldown.clear()
        commitmentCooldown.clear()
        super.onDestroy()
    }

    companion object {
        private val _connectionState = MutableStateFlow(false)
        val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

        private const val TAG = "Blocker2.Accessibility"
        private const val MIN_SCAN_INTERVAL_MS = 350L
        private const val MATCH_COOLDOWN_MS = 2_000L
        private const val COMMITMENT_ENFORCEMENT_COOLDOWN_MS = 1_000L
        private const val MAX_NODES = 500
        private const val MAX_TEXT_LENGTH = 12_000
    }
}
