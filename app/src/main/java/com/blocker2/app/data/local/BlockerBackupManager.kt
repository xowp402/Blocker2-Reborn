package com.blocker2.app.data.local

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.blocker2.app.data.local.entity.*
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException

/** Versioned JSON backup; restore validates the full document before an atomic table replacement. */
object BlockerBackupManager {
    private const val FORMAT = "blocker2-backup"
    private const val VERSION = 1
    private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
    private val gson = Gson()

    private data class BackupEnvelope(
        val format: String,
        val version: Int,
        val exportedAt: Long,
        val wordRules: List<WordRuleEntity>,
        val telegramRules: List<TelegramFilterRuleEntity>,
        val blockedSites: List<SiteRuleEntity>,
        val allowedSites: List<AllowedSiteEntity>,
        val monitoredApps: List<MonitoredAppEntity>,
        val activityLogs: List<ActivityLogEntity>,
        val activeBlocks: List<ActiveAppBlockEntity>,
        val commitmentSession: CommitmentSessionEntity?
    )

    suspend fun exportTo(resolver: ContentResolver, uri: Uri, database: BlockerDatabase) {
        val data = database.withTransaction {
            BackupEnvelope(
                FORMAT, VERSION, System.currentTimeMillis(),
                database.wordRuleDao().exportAll(),
                database.telegramFilterRuleDao().exportAll(),
                database.siteRuleDao().exportAll(),
                database.siteRuleDao().exportAllowedSites(),
                database.monitoredAppDao().exportAll(),
                database.activityLogDao().exportAll(),
                database.activeAppBlockDao().exportAll(),
                database.commitmentSessionDao().exportAll().firstOrNull()
            )
        }
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("امکان ساخت فایل پشتیبان وجود ندارد.")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(gson.toJson(data)) }
    }

    suspend fun importFrom(resolver: ContentResolver, uri: Uri, database: BlockerDatabase) {
        val json = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (totalBytes > MAX_BACKUP_BYTES - count) {
                    throw IOException("فایل پشتیبان بیش از حد بزرگ است.")
                }
                output.write(buffer, 0, count)
                totalBytes += count
            }
            String(output.toByteArray(), Charsets.UTF_8)
        } ?: throw IOException("فایل انتخاب‌شده قابل خواندن نیست.")
        if (json.isBlank()) {
            throw IOException("فایل پشتیبان خالی است.")
        }
        val data = try {
            gson.fromJson(json, BackupEnvelope::class.java)
        } catch (error: RuntimeException) {
            throw IOException("ساختار فایل پشتیبان معتبر نیست.", error)
        } ?: throw IOException("فایل پشتیبان معتبر نیست.")
        if (data.format != FORMAT || data.version != VERSION) {
            throw IOException("قالب یا نسخهٔ این فایل پشتیبان پشتیبانی نمی‌شود.")
        }
        try {
            validate(data)
        } catch (error: RuntimeException) {
            throw IOException("اطلاعات فایل پشتیبان ناقص یا نامعتبر است.", error)
        }

        database.withTransaction {
            database.wordRuleDao().clearForImport()
            database.telegramFilterRuleDao().clearForImport()
            database.siteRuleDao().clearForImport()
            database.siteRuleDao().clearAllowedSitesForImport()
            database.monitoredAppDao().clearForImport()
            database.activityLogDao().clearForImport()
            database.activeAppBlockDao().clearForImport()
            database.commitmentSessionDao().clearForImport()

            database.wordRuleDao().importAll(data.wordRules)
            database.telegramFilterRuleDao().importAll(data.telegramRules)
            database.siteRuleDao().importAll(data.blockedSites)
            database.siteRuleDao().importAllowedSites(data.allowedSites)
            database.monitoredAppDao().importAll(data.monitoredApps)
            database.activityLogDao().importAll(data.activityLogs)
            database.activeAppBlockDao().importAll(data.activeBlocks)
            data.commitmentSession?.let {
                database.commitmentSessionDao().importAll(listOf(it))
            }
        }
    }

    private fun validate(data: BackupEnvelope) {
        fun uniquePositive(ids: List<Long>, label: String) {
            if (ids.any { it <= 0L } || ids.distinct().size != ids.size) {
                throw IOException("شناسه‌های $label معتبر یا یکتا نیستند.")
            }
        }
        uniquePositive(data.wordRules.map { it.id }, "واژه‌ها")
        uniquePositive(data.telegramRules.map { it.id }, "تلگرام")
        uniquePositive(data.blockedSites.map { it.id }, "سایت‌های مسدود")
        uniquePositive(data.allowedSites.map { it.id }, "سایت‌های مجاز")
        uniquePositive(data.activityLogs.map { it.id }, "گزارش‌ها")
        if (data.monitoredApps.any { it.packageName.isBlank() } ||
            data.monitoredApps.map { it.packageName }.distinct().size != data.monitoredApps.size
        ) throw IOException("فهرست برنامه‌ها معتبر نیست.")
        if (data.wordRules.any { it.text.isBlank() || it.normalizedText.isBlank() } ||
            data.telegramRules.any { it.value.isBlank() || it.normalizedValue.isBlank() } ||
            data.blockedSites.any { it.pattern.isBlank() || it.normalizedPattern.isBlank() } ||
            data.allowedSites.any { it.pattern.isBlank() || it.normalizedPattern.isBlank() }
        ) throw IOException("یکی از قوانین فایل پشتیبان ناقص است.")
        if (data.activeBlocks.any { it.packageName.isBlank() || it.expiresAt <= it.createdAt }) {
            throw IOException("اطلاعات مسدودی‌های فعال معتبر نیست.")
        }
        if (data.commitmentSession != null &&
            (data.commitmentSession.id != 1 || data.commitmentSession.expiresAt <= data.commitmentSession.startedAt)
        ) throw IOException("اطلاعات جلسهٔ تعهد معتبر نیست.")
    }
}
