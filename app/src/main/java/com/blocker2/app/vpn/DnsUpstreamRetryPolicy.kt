package com.blocker2.app.vpn

/**
 * Runs DNS upstream attempts in order. A retryable failure advances to the
 * next resolver; a fatal failure stops immediately to preserve VPN safety.
 */
internal object DnsUpstreamRetryPolicy {
    sealed class AttemptResult {
        data class Success(val response: ByteArray) : AttemptResult()
        object Retry : AttemptResult()
        object Abort : AttemptResult()
    }

    fun firstValidResponse(
        upstreams: List<String>,
        attempt: (String) -> AttemptResult
    ): ByteArray? {
        for (upstream in upstreams) {
            when (val result = attempt(upstream)) {
                is AttemptResult.Success -> return result.response
                AttemptResult.Retry -> continue
                AttemptResult.Abort -> return null
            }
        }
        return null
    }
}
