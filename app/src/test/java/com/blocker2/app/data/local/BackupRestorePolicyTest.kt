package com.blocker2.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePolicyTest {
    @Test
    fun activeCommitmentBlocksRestore() {
        assertTrue(BackupRestorePolicy.blocksRestore(commitmentExpiresAt = 10_001L, now = 10_000L))
    }

    @Test
    fun commitmentExpiringAtCurrentTimeDoesNotBlockRestore() {
        assertFalse(BackupRestorePolicy.blocksRestore(commitmentExpiresAt = 10_000L, now = 10_000L))
    }

    @Test
    fun expiredCommitmentDoesNotBlockRestore() {
        assertFalse(BackupRestorePolicy.blocksRestore(commitmentExpiresAt = 9_999L, now = 10_000L))
    }

    @Test
    fun missingCommitmentDoesNotBlockRestore() {
        assertFalse(BackupRestorePolicy.blocksRestore(commitmentExpiresAt = null, now = 10_000L))
    }
}
