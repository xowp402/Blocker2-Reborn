package com.blocker2.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsUpstreamRetryPolicyTest {
    @Test fun retriesNextResolverAfterRetryableFailure() {
        val attempted = mutableListOf<String>()
        val expected = byteArrayOf(1, 2, 3)

        val result = DnsUpstreamRetryPolicy.firstValidResponse(
            listOf("resolver-a", "resolver-b")
        ) { upstream ->
            attempted += upstream
            if (upstream == "resolver-a") {
                DnsUpstreamRetryPolicy.AttemptResult.Retry
            } else {
                DnsUpstreamRetryPolicy.AttemptResult.Success(expected)
            }
        }

        assertEquals(listOf("resolver-a", "resolver-b"), attempted)
        assertArrayEquals(expected, result)
    }

    @Test fun abortsImmediatelyWhenSocketCannotBeProtected() {
        val attempted = mutableListOf<String>()

        val result = DnsUpstreamRetryPolicy.firstValidResponse(
            listOf("resolver-a", "resolver-b")
        ) { upstream ->
            attempted += upstream
            DnsUpstreamRetryPolicy.AttemptResult.Abort
        }

        assertEquals(listOf("resolver-a"), attempted)
        assertNull(result)
    }

    @Test fun returnsNullWhenEveryResolverFailsRetryably() {
        val attempted = mutableListOf<String>()

        val result = DnsUpstreamRetryPolicy.firstValidResponse(
            listOf("resolver-a", "resolver-b")
        ) { upstream ->
            attempted += upstream
            DnsUpstreamRetryPolicy.AttemptResult.Retry
        }

        assertEquals(listOf("resolver-a", "resolver-b"), attempted)
        assertNull(result)
    }

    @Test fun stopsAfterFirstSuccessfulResolver() {
        val attempted = mutableListOf<String>()
        val expected = byteArrayOf(9)

        val result = DnsUpstreamRetryPolicy.firstValidResponse(
            listOf("resolver-a", "resolver-b", "resolver-c")
        ) { upstream ->
            attempted += upstream
            DnsUpstreamRetryPolicy.AttemptResult.Success(expected)
        }

        assertEquals(listOf("resolver-a"), attempted)
        assertArrayEquals(expected, result)
    }

    @Test fun emptyResolverListDoesNotAttemptAnything() {
        val result = DnsUpstreamRetryPolicy.firstValidResponse(emptyList()) {
            throw AssertionError("No resolver should be attempted")
        }
        assertNull(result)
    }
}
