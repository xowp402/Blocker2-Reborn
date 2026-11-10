package com.blocker2.app.data.repository

import com.blocker2.app.core.text.WordNormalizer
import com.blocker2.app.data.local.dao.WordRuleDao
import com.blocker2.app.data.local.entity.WordRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local-only access layer for word rules. Input is normalized before persistence so
 * the filtering engine can compare normalized values consistently.
 */
class WordRuleRepository(
    private val dao: WordRuleDao
) {
    fun observeAll(): Flow<List<WordRuleEntity>> = dao.observeAll()

    fun observeEnabled(): Flow<List<WordRuleEntity>> = dao.observeEnabled()

    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()

    fun observeEnabledCount(): Flow<Int> = dao.observeEnabledCount()

    suspend fun save(
        text: String,
        category: String = "GENERAL",
        language: String = "AUTO",
        enabled: Boolean = true,
        matchMode: String = "NORMALIZED_WORD",
        reaction: String = "WARN_AND_EXIT",
        blockDurationMillis: Long = 120_000L,
        customWarning: String? = null,
        targetPackages: Set<String> = emptySet(),
        statisticsEnabled: Boolean = true
    ): Long {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Word or phrase cannot be empty." }
        require(blockDurationMillis >= 0L) { "Block duration cannot be negative." }

        val now = System.currentTimeMillis()
        return dao.upsert(
            WordRuleEntity(
                text = cleanText,
                normalizedText = WordNormalizer.normalize(cleanText),
                category = category,
                language = language,
                enabled = enabled,
                matchMode = matchMode,
                reaction = reaction,
                blockDurationMillis = blockDurationMillis,
                customWarning = customWarning?.takeIf { it.isNotBlank() },
                targetPackages = targetPackages.sorted().joinToString("|"),
                statisticsEnabled = statisticsEnabled,
                updatedAt = now
            )
        )
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Boolean =
        dao.setEnabled(id, enabled, System.currentTimeMillis()) > 0

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun recordDetection(id: Long): Boolean =
        dao.incrementDetectionCount(id, System.currentTimeMillis()) > 0
}
