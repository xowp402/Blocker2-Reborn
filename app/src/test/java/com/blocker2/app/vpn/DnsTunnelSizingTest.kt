package com.blocker2.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DnsTunnelSizingTest {
    @Test fun mtuFitsLargestAcceptedIpv6DnsResponse() {
        assertEquals(
            DnsTunnelSizing.TUN_MTU,
            DnsTunnelSizing.packetSize(
                6,
                DnsTunnelSizing.MAX_UPSTREAM_DNS_RESPONSE_BYTES
            )
        )
    }

    @Test fun ipv4ResponseUsesSmallerHeaderBudget() {
        assertEquals(
            DnsTunnelSizing.MAX_UPSTREAM_DNS_RESPONSE_BYTES + 28,
            DnsTunnelSizing.packetSize(
                4,
                DnsTunnelSizing.MAX_UPSTREAM_DNS_RESPONSE_BYTES
            )
        )
    }

    @Test fun rejectsPayloadLargerThanReceiveBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            DnsTunnelSizing.packetSize(
                6,
                DnsTunnelSizing.MAX_UPSTREAM_DNS_RESPONSE_BYTES + 1
            )
        }
    }

    @Test fun rejectsUnsupportedIpVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            DnsTunnelSizing.packetSize(5, 64)
        }
    }
}
