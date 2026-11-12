package com.blocker2.app.data.local

/**
 * Centralizes the commitment guard used by backup restore.
 *
 * An expired or absent session must not prevent recovery from a backup;
 * a session whose expiry is still in the future must remain protected.
 */
internal object BackupRestorePolicy {
    fun blocksRestore(commitmentExpiresAt: Long?, now: Long): Boolean =
        commitmentExpiresAt != null && commitmentExpiresAt > now
}
