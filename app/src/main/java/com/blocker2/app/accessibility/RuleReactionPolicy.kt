package com.blocker2.app.accessibility

import java.util.Locale

/** Keeps word and Telegram reaction handling consistent. */
internal object RuleReactionPolicy {
    data class Decision(
        val requiresExit: Boolean,
        val startsTimedBlock: Boolean,
        val outcome: String
    )

    fun evaluate(
        reaction: String,
        durationMillis: Long,
        timedBlockAlreadyCreated: Boolean
    ): Decision {
        val normalizedReaction = reaction.trim().uppercase(Locale.ROOT)
        val requiresExit = normalizedReaction == "WARN_AND_EXIT" ||
            normalizedReaction == "EXIT_ONLY" ||
            normalizedReaction == "TEMPORARY_BLOCK"
        val startsTimedBlock = !timedBlockAlreadyCreated &&
            (normalizedReaction == "WARN_AND_EXIT" ||
                normalizedReaction == "TEMPORARY_BLOCK") &&
            durationMillis > 0L
        val outcome = when {
            normalizedReaction == "LOG_ONLY" -> "DETECTED"
            normalizedReaction == "EXIT_ONLY" -> "EXIT_REQUESTED"
            startsTimedBlock -> "TEMPORARY_BLOCK_STARTED"
            requiresExit -> "EXIT_REQUESTED"
            else -> "DETECTED"
        }
        return Decision(
            requiresExit = requiresExit,
            startsTimedBlock = startsTimedBlock,
            outcome = outcome
        )
    }
}
