package com.blocker2.app.data.repository

import com.blocker2.app.core.text.WordNormalizer
import com.blocker2.app.data.local.dao.TelegramFilterRuleDao
import com.blocker2.app.data.local.entity.TelegramFilterRuleEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class TelegramRuleRepository(
    private val dao: TelegramFilterRuleDao
) {
    fun observeAll(): Flow<List<TelegramFilterRuleEntity>> = dao.observeAll()

    fun observeEnabled(): Flow<List<TelegramFilterRuleEntity>> = dao.observeEnabled()

    suspend fun save(
        ruleType: String,
        value: String,
        enabled: Boolean = true,
        reaction: String = "WARN_AND_EXIT",
        blockDurationMillis: Long = 120_000L
    ): Long {
        val type = ruleType.uppercase(Locale.ROOT)
        require(type in SUPPORTED_TYPES) { "Unsupported Telegram rule type: $ruleType" }
        val cleanValue = value.trim()
        require(cleanValue.isNotEmpty()) { "Telegram rule value cannot be empty." }
        require(blockDurationMillis >= 0L) { "Block duration cannot be negative." }

        val normalized = when (type) {
            "WORD", "PHRASE" -> WordNormalizer.normalize(cleanValue)
            else -> cleanValue.lowercase(Locale.ROOT)
        }
        val now = System.currentTimeMillis()
        return dao.upsert(
            TelegramFilterRuleEntity(
                ruleType = type,
                value = cleanValue,
                normalizedValue = normalized,
                enabled = enabled,
                reaction = reaction,
                blockDurationMillis = blockDurationMillis,
                updatedAt = now
            )
        )
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Boolean =
        dao.setEnabled(id, enabled, System.currentTimeMillis()) > 0

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun recordDetection(id: Long): Boolean =
        dao.incrementDetectionCount(id, System.currentTimeMillis()) > 0

    private companion object {
        val SUPPORTED_TYPES = setOf("WORD", "PHRASE", "CHANNEL_URL", "CHANNEL_ID", "USERNAME")
    }
}
