package com.blocker2.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.blocker2.app.Blocker2Application
import com.blocker2.app.MainActivity
import com.blocker2.app.R
import com.blocker2.app.core.site.SiteRuleMatcher
import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS-only local VPN. It routes only queries for the synthetic DNS address into
 * the TUN interface; ordinary app traffic is not routed through this service.
 */
class LocalDnsVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    @Volatile private var blockedRules: List<SiteRuleEntity> = emptyList()
    @Volatile private var allowedRules: List<AllowedSiteEntity> = emptyList()

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running.get()) {
            try {
                establishTunnel()
                loadRulesAndStartPacketLoop()
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Unable to establish local DNS VPN", error)
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun establishTunnel() {
        val prepared = prepare(this)
        check(prepared == null) { "VPN permission has not been granted." }

        val descriptor = Builder()
            .setSession("Blocker2 local DNS filter")
            .setMtu(1500)
            .setBlocking(true)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .addAddress(TUN_IPV6_ADDRESS, 128)
            .addDnsServer(DNS_IPV6_ADDRESS)
            .addRoute(DNS_IPV6_ADDRESS, 128)
            .establish()
        checkNotNull(descriptor) { "Android did not create the VPN interface." }
        tunnel = descriptor
        running.set(true)
    }

    private fun loadRulesAndStartPacketLoop() {
        val dao = (application as Blocker2Application).database.siteRuleDao()
        serviceScope.launch {
            // Load the persisted snapshot before accepting the first DNS query.
            blockedRules = dao.observeEnabledBlockedSites().first()
            allowedRules = dao.observeAllowedSites().first()
            // The user may stop/revoke the VPN while Room is loading its initial
            // snapshot. Never start a packet worker after the tunnel was released.
            if (!running.get() || tunnel == null) return@launch
            launch {
                combine(
                    dao.observeEnabledBlockedSites(),
                    dao.observeAllowedSites()
                ) { blocked, allowed -> blocked to allowed }
                    .collect { (blocked, allowed) ->
                        blockedRules = blocked
                        allowedRules = allowed
                    }
            }
            if (running.get() && tunnel != null) startPacketLoop()
        }
    }

    private fun startPacketLoop() {
        if (!running.get()) return
        val descriptor = tunnel ?: return
        worker = Thread({
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val packetBuffer = ByteArray(32767)
            try {
                while (running.get()) {
                    val length = input.read(packetBuffer)
                    if (length <= 0) continue
                    val request = packetBuffer.copyOf(length)
                    val parsed = parseDnsPacket(request)
                    if (parsed == null) {
                        // Do not silently drop unsupported DNS transports (for example TCP/53).
                        // Release the VPN so Android can fall back to its regular resolver.
                        android.util.Log.w(TAG, "Unsupported DNS packet; disabling site filter safely")
                        stopVpn()
                        break
                    }
                    val decision = SiteRuleMatcher.evaluate(
                        parsed.hostname,
                        blockedRules,
                        allowedRules
                    )
                    val dnsResponse = if (decision == SiteRuleMatcher.Decision.BLOCK) {
                        buildNxDomain(parsed.dnsPayload)
                    } else {
                        forwardDns(parsed.dnsPayload)
                    }
                    if (dnsResponse == null) {
                        // Fail open: disable the site VPN so Android can resume its normal DNS.
                        // Word filtering runs in a separate Accessibility service and is unaffected.
                        android.util.Log.e(TAG, "DNS upstream failed; disabling site filter")
                        stopVpn()
                        break
                    }

                    val responsePacket = buildUdpResponse(
                        parsed = parsed,
                        payload = dnsResponse
                    )
                    output.write(responsePacket)
                    output.flush()
                }
            } catch (error: Exception) {
                if (running.get()) android.util.Log.e(TAG, "DNS packet loop stopped", error)
            } finally {
                input.close()
                output.close()
                if (running.get()) stopVpn()
            }
        }, "Blocker2-DNS-TUN").apply {
            isDaemon = true
            start()
        }
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        for (upstream in UPSTREAM_DNS_SERVERS) {
            try {
                val responseBytes = DatagramSocket().use { socket ->
                    if (!protect(socket)) return null
                    socket.soTimeout = UPSTREAM_TIMEOUT_MS
                    val server = InetAddress.getByName(upstream)
                    socket.send(DatagramPacket(query, query.size, server, DNS_PORT))
                    val responseBuffer = ByteArray(4096)
                    val response = DatagramPacket(responseBuffer, responseBuffer.size)
                    socket.receive(response)
                    val responseBytes = response.data.copyOfRange(
                        response.offset,
                        response.offset + response.length
                    )
                    if (response.address != server || response.port != DNS_PORT ||
                        !DnsResponseValidator.isMatchingResponse(query, responseBytes)
                    ) {
                        null
                    } else {
                        responseBytes
                    }
                }
                if (responseBytes != null) return responseBytes
            } catch (error: Exception) {
                android.util.Log.w(TAG, "DNS upstream $upstream failed", error)
            }
        }
        return null
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, LocalDnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("فیلتر DNS محلی Blocker2 فعال است")
            .setContentText("فقط درخواست‌های DNS دامنه‌ها بررسی می‌شوند.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "توقف", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "فیلتر سایت‌ها", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun stopVpn() {
        if (!running.getAndSet(false) && tunnel == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Clear the published descriptor before closing it so asynchronous
        // startup cannot mistake a released tunnel for an active one.
        val descriptor = tunnel
        tunnel = null
        try { descriptor?.close() } catch (error: Exception) {
            android.util.Log.w(TAG, "Error while closing DNS tunnel", error)
        }
        blockedRules = emptyList()
        allowedRules = emptyList()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        running.set(false)
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class ParsedDnsPacket(
        val ipVersion: Int,
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val identification: Int,
        val dnsPayload: ByteArray,
        val hostname: String
    )

    private fun parseDnsPacket(packet: ByteArray): ParsedDnsPacket? {
        if (packet.isEmpty()) return null
        return when ((packet[0].toInt() ushr 4) and 0x0f) {
            4 -> parseIpv4DnsPacket(packet)
            6 -> parseIpv6DnsPacket(packet)
            else -> null
        }
    }

    private fun parseIpv4DnsPacket(packet: ByteArray): ParsedDnsPacket? {
        if (packet.size < 28) return null
        val ipHeaderLength = (packet[0].toInt() and 0x0f) * 4
        val totalLength = readU16(packet, 2)
        if (ipHeaderLength < 20 || packet.size < ipHeaderLength + 8) return null
        if (!IpPacketValidator.isValidIpv4Header(packet, totalLength)) return null
        if ((packet[9].toInt() and 0xff) != IPPROTO_UDP) return null
        val sourcePort = readU16(packet, ipHeaderLength)
        val destinationPort = readU16(packet, ipHeaderLength + 2)
        if (destinationPort != DNS_PORT) return null
        val udpLength = readU16(packet, ipHeaderLength + 4)
        if (udpLength != totalLength - ipHeaderLength ||
            !IpPacketValidator.isValidUdpLength(packet, ipHeaderLength, udpLength, totalLength - ipHeaderLength) ||
            !IpPacketValidator.isValidIpv4UdpChecksum(packet, ipHeaderLength, totalLength)
        ) return null
        val dnsStart = ipHeaderLength + 8
        val dns = packet.copyOfRange(dnsStart, ipHeaderLength + udpLength)
        val hostname = DnsQuestionParser.readHostname(dns) ?: return null
        return ParsedDnsPacket(
            ipVersion = 4,
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            identification = readU16(packet, 4),
            dnsPayload = dns,
            hostname = hostname
        )
    }

    private fun parseIpv6DnsPacket(packet: ByteArray): ParsedDnsPacket? {
        // This implementation intentionally accepts only the base IPv6 header
        // with UDP as its immediate next-header. Extension headers are not parsed.
        if (packet.size < 48 || (packet[6].toInt() and 0xff) != IPPROTO_UDP) return null
        val payloadLength = readU16(packet, 4)
        if (payloadLength < 8 || !IpPacketValidator.isExactIpv6PacketLength(packet, payloadLength)) return null
        val sourcePort = readU16(packet, 40)
        val destinationPort = readU16(packet, 42)
        if (destinationPort != DNS_PORT) return null
        val udpLength = readU16(packet, 44)
        if (udpLength != payloadLength ||
            !IpPacketValidator.isValidUdpLength(packet, 40, udpLength, payloadLength) ||
            !IpPacketValidator.isValidIpv6UdpChecksum(packet, 40, udpLength)
        ) return null
        val dns = packet.copyOfRange(48, 40 + udpLength)
        val hostname = DnsQuestionParser.readHostname(dns) ?: return null
        return ParsedDnsPacket(
            ipVersion = 6,
            sourceAddress = packet.copyOfRange(8, 24),
            destinationAddress = packet.copyOfRange(24, 40),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            identification = 0,
            dnsPayload = dns,
            hostname = hostname
        )
    }


    private fun buildNxDomain(query: ByteArray): ByteArray? = DnsNxDomainBuilder.build(query)

    private fun buildUdpResponse(
        parsed: ParsedDnsPacket,
        payload: ByteArray
    ): ByteArray = if (parsed.ipVersion == 6) {
        DnsIpPacketBuilder.buildIpv6Response(
            sourceAddress = parsed.destinationAddress,
            destinationAddress = parsed.sourceAddress,
            sourcePort = parsed.destinationPort,
            destinationPort = parsed.sourcePort,
            payload = payload
        )
    } else {
        DnsIpPacketBuilder.buildIpv4Response(
            sourceAddress = parsed.destinationAddress,
            destinationAddress = parsed.sourceAddress,
            sourcePort = parsed.destinationPort,
            destinationPort = parsed.sourcePort,
            identification = parsed.identification,
            payload = payload
        )
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    companion object {
        const val ACTION_START = "com.blocker2.app.vpn.START"
        const val ACTION_STOP = "com.blocker2.app.vpn.STOP"
        private const val TAG = "Blocker2LocalDnsVPN"
        private const val CHANNEL_ID = "local_dns_vpn"
        private const val NOTIFICATION_ID = 2402
        private const val TUN_ADDRESS = "10.111.0.1"
        private const val DNS_ADDRESS = "10.111.0.2"
        private const val TUN_IPV6_ADDRESS = "fd11:111::1"
        private const val DNS_IPV6_ADDRESS = "fd11:111::2"
        private val UPSTREAM_DNS_SERVERS = arrayOf("1.1.1.1", "8.8.8.8")
        private const val DNS_PORT = 53
        private const val IPPROTO_UDP = 17
        private const val UPSTREAM_TIMEOUT_MS = 3000
    }
}
