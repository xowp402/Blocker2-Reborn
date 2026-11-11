package com.blocker2.app.accessibility

import java.util.concurrent.atomic.AtomicBoolean

/** Allows only one accessibility snapshot to be processed at a time. */
internal class ScanProcessingGate {
    private val processing = AtomicBoolean(false)

    fun tryAcquire(): Boolean = processing.compareAndSet(false, true)

    fun release() {
        check(processing.compareAndSet(true, false)) {
            "Cannot release a scan-processing gate that is not acquired."
        }
    }

    fun isProcessing(): Boolean = processing.get()
}
