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
            startPacketLoop()
        }
    }

    private fun startPacketLoop() {
        val descriptor = checkNotNull(tunnel)
        worker = Thread({
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val packetBuffer = ByteArray(32767)
            try {
                while (running.get()) {
                    val length = input.read(packetBuffer)
                    if (length <= 0) continue
                    val request = packetBuffer.copyOf(length)
                    val parsed = parseDnsPacket(request) ?: continue
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
                        request = request,
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
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                socket.soTimeout = UPSTREAM_TIMEOUT_MS
                val server = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(query, query.size, server, DNS_PORT))
                val responseBuffer = ByteArray(4096)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)
                response.data.copyOf(response.length)
            }
        } catch (_: SocketTimeoutException) {
            null
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Upstream DNS request failed", error)
            null
        }
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
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
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
        if (ipHeaderLength < 20 || packet.size < ipHeaderLength + 8) return null
        if ((packet[9].toInt() and 0xff) != IPPROTO_UDP) return null
        val sourcePort = readU16(packet, ipHeaderLength)
        val destinationPort = readU16(packet, ipHeaderLength + 2)
        if (destinationPort != DNS_PORT) return null
        val udpLength = readU16(packet, ipHeaderLength + 4)
        if (udpLength < 8 || packet.size < ipHeaderLength + udpLength) return null
        val dnsStart = ipHeaderLength + 8
        val dns = packet.copyOfRange(dnsStart, ipHeaderLength + udpLength)
        val hostname = readQuestionName(dns) ?: return null
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
        if (payloadLength < 8 || packet.size < 40 + payloadLength) return null
        val sourcePort = readU16(packet, 40)
        val destinationPort = readU16(packet, 42)
        if (destinationPort != DNS_PORT) return null
        val udpLength = readU16(packet, 44)
        if (udpLength < 8 || udpLength > payloadLength) return null
        val dns = packet.copyOfRange(48, 40 + udpLength)
        val hostname = readQuestionName(dns) ?: return null
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

    private fun readQuestionName(dns: ByteArray): String? {
        if (dns.size < 17 || readU16(dns, 4) != 1) return null
        val labels = ArrayList<String>()
        var offset = 12
        var jumps = 0
        var consumedEnd = -1
        while (offset < dns.size) {
            val size = dns[offset].toInt() and 0xff
            if (size == 0) {
                if (consumedEnd < 0) consumedEnd = offset + 1
                return if (consumedEnd + 4 <= dns.size) labels.joinToString(".") else null
            }
            if ((size and 0xc0) == 0xc0) {
                if (offset + 1 >= dns.size || jumps++ > 8) return null
                if (consumedEnd < 0) consumedEnd = offset + 2
                offset = ((size and 0x3f) shl 8) or (dns[offset + 1].toInt() and 0xff)
                continue
            }
            if (size > 63 || offset + 1 + size > dns.size) return null
            val label = dns.copyOfRange(offset + 1, offset + 1 + size).toString(Charsets.US_ASCII)
            labels += label
            offset += size + 1
        }
        return null
    }

    private fun buildNxDomain(query: ByteArray): ByteArray? {
        if (query.size < 17 || readU16(query, 4) != 1) return null
        var offset = 12
        while (offset < query.size) {
            val size = query[offset].toInt() and 0xff
            if (size == 0) {
                offset++
                break
            }
            if ((size and 0xc0) == 0xc0 || size > 63 || offset + size + 1 > query.size) return null
            offset += size + 1
        }
        if (offset + 4 > query.size) return null
        val questionEnd = offset + 4
        val response = query.copyOf(questionEnd)
        val flags = readU16(query, 2)
        val responseFlags = (flags and 0x7900) or 0x8083
        writeU16(response, 2, responseFlags)
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    private fun buildUdpResponse(
        parsed: ParsedDnsPacket,
        request: ByteArray,
        payload: ByteArray
    ): ByteArray = if (parsed.ipVersion == 6) {
        buildIpv6UdpResponse(parsed, payload)
    } else {
        buildIpv4UdpResponse(parsed, request, payload)
    }

    private fun buildIpv4UdpResponse(
        parsed: ParsedDnsPacket,
        request: ByteArray,
        payload: ByteArray
    ): ByteArray {
        val ipLength = 20
        val udpLength = 8 + payload.size
        val result = ByteArray(ipLength + udpLength)
        result[0] = 0x45
        writeU16(result, 2, result.size)
        writeU16(result, 4, parsed.identification)
        writeU16(result, 6, 0x4000)
        result[8] = 64
        result[9] = IPPROTO_UDP.toByte()
        System.arraycopy(parsed.destinationAddress, 0, result, 12, 4)
        System.arraycopy(parsed.sourceAddress, 0, result, 16, 4)
        writeU16(result, 10, checksum(result, 0, ipLength))
        writeU16(result, ipLength, parsed.destinationPort)
        writeU16(result, ipLength + 2, parsed.sourcePort)
        writeU16(result, ipLength + 4, udpLength)
        writeU16(result, ipLength + 6, 0)
        System.arraycopy(payload, 0, result, ipLength + 8, payload.size)
        return result
    }

    private fun buildIpv6UdpResponse(parsed: ParsedDnsPacket, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val result = ByteArray(40 + udpLength)
        result[0] = 0x60
        writeU16(result, 4, udpLength)
        result[6] = IPPROTO_UDP.toByte()
        result[7] = 64
        System.arraycopy(parsed.destinationAddress, 0, result, 8, 16)
        System.arraycopy(parsed.sourceAddress, 0, result, 24, 16)
        writeU16(result, 40, parsed.destinationPort)
        writeU16(result, 42, parsed.sourcePort)
        writeU16(result, 44, udpLength)
        writeU16(result, 46, 0)
        System.arraycopy(payload, 0, result, 48, payload.size)
        val udpChecksum = ipv6UdpChecksum(
            source = parsed.destinationAddress,
            destination = parsed.sourceAddress,
            udpPacket = result,
            udpOffset = 40,
            udpLength = udpLength
        )
        writeU16(result, 46, if (udpChecksum == 0) 0xffff else udpChecksum)
        return result
    }

    private fun ipv6UdpChecksum(
        source: ByteArray,
        destination: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L
        fun addBytes(bytes: ByteArray, start: Int = 0, length: Int = bytes.size) {
            var i = start
            val end = start + length
            while (i + 1 < end) {
                sum += (((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)).toLong()
                i += 2
            }
            if (i < end) sum += ((bytes[i].toInt() and 0xff) shl 8).toLong()
        }
        addBytes(source)
        addBytes(destination)
        sum += ((udpLength ushr 16) and 0xffff).toLong()
        sum += (udpLength and 0xffff).toLong()
        sum += IPPROTO_UDP.toLong()
        addBytes(udpPacket, udpOffset, udpLength)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun checksum(data: ByteArray, start: Int, length: Int): Int {
        var sum = 0L
        var index = start
        val end = start + length
        while (index + 1 < end) {
            sum += (((data[index].toInt() and 0xff) shl 8) or (data[index + 1].toInt() and 0xff)).toLong()
            index += 2
        }
        if (index < end) sum += ((data[index].toInt() and 0xff) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
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
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val DNS_PORT = 53
        private const val IPPROTO_UDP = 17
        private const val UPSTREAM_TIMEOUT_MS = 3000
    }
}
