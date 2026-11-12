package com.blocker2.app.accessibility

/** Separates cooldown-limited analytics from enforcement of an active timed block. */
internal object DetectionProcessingPolicy {
    fun shouldProcess(
        cooldownAccepted: Boolean,
        activeTimedBlock: Boolean,
        startsTimedBlock: Boolean
    ): Boolean = cooldownAccepted || (activeTimedBlock && startsTimedBlock)
}
