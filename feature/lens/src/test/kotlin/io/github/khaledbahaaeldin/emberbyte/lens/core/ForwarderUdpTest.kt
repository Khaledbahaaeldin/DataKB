package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SelectableChannel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ForwarderUdpTest {

    private lateinit var fakeTun: FakeTun
    private lateinit var forwarder: Forwarder
    private lateinit var forwarderThread: Thread
    private lateinit var testListener: TestFlowListener
    private var clock = TestClock()
    private val parser = PacketParser()

    private val protector = object : SocketProtector {
        override fun protect(socket: Socket): Boolean = true
        override fun protect(socket: DatagramSocket): Boolean = true
        override fun protect(channel: SelectableChannel): Boolean = true
    }

    private val ownerResolver = object : OwnerResolver {
        override fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int = 10042
    }

    class TestFlowListener : FlowListener {
        val openedFlows = ConcurrentHashMap<Long, FlowInfo>()
        val closedFlows = ConcurrentHashMap<Long, Boolean>()
        val totalRx = AtomicLong(0)
        val totalTx = AtomicLong(0)
        val names = ConcurrentHashMap<Long, String>()
        val dnsAnswers = java.util.concurrent.CopyOnWriteArrayList<DnsAnswer>()
        val dnsLatch = CountDownLatch(1)

        override fun onFlowOpened(flow: FlowInfo) {
            openedFlows[flow.flowId] = flow
        }

        override fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long) {
            totalRx.addAndGet(rxBytes)
            totalTx.addAndGet(txBytes)
        }

        override fun onName(flowId: Long, name: String) {
            names[flowId] = name
        }

        override fun onDns(answers: List<DnsAnswer>) {
            dnsAnswers.addAll(answers)
            dnsLatch.countDown()
        }

        override fun onFlowClosed(flowId: Long) {
            closedFlows[flowId] = true
        }
    }

    class TestClock(var currentInstant: Instant = Instant.parse("2026-10-11T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = currentInstant
        fun advance(duration: Duration) {
            currentInstant = currentInstant.plus(duration)
        }
    }

    @Before
    fun setUp() {
        clock = TestClock()
        fakeTun = FakeTun()
        testListener = TestFlowListener()
        forwarder = Forwarder(
            tun = fakeTun,
            protector = protector,
            owner = ownerResolver,
            listener = testListener,
            clock = clock,
            maxFlows = 1024,
            mtu = 1400
        )
        forwarderThread = Thread({ forwarder.run() }, "Forwarder-Udp-Thread")
        forwarderThread.start()
    }

    @After
    fun tearDown() {
        forwarder.stop()
        fakeTun.close()
        forwarderThread.join(2000)
    }

    @Test(timeout = 10000)
    fun udp_echo_roundtrip_ipv4() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverRunning = AtomicBoolean(true)
        val serverThread = Thread {
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            try {
                server.receive(packet)
                server.send(DatagramPacket(packet.data, packet.offset, packet.length, packet.socketAddress))
            } catch (_: Exception) {}
        }
        serverThread.start()

        val clientSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53001)
        val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        val payload = "Hello UDP v4".toByteArray()

        val udpPkt = PacketBuilder.ipv4Udp(clientSrc, serverDst, payload)
        fakeTun.toForwarder.offer(udpPkt)

        val start = System.currentTimeMillis()
        var receivedEcho: ByteArray? = null
        while (System.currentTimeMillis() - start < 5000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == serverDst && parsed.dst == clientSrc) {
                receivedEcho = ByteArray(parsed.payloadLength)
                System.arraycopy(resp, parsed.payloadOffset, receivedEcho, 0, parsed.payloadLength)
                break
            }
        }

        server.close()
        serverThread.join(1000)

        assertNotNull("Should receive UDP echo response", receivedEcho)
        assertArrayEquals(payload, receivedEcho)
    }

    @Test(timeout = 10000)
    fun udp_echo_roundtrip_ipv6() {
        val ipv6Supported = try {
            val s = DatagramSocket(0, InetAddress.getByName("::1"))
            s.close()
            true
        } catch (_: Exception) {
            false
        }
        Assume.assumeTrue("IPv6 not supported on this host", ipv6Supported)

        val server = DatagramSocket(0, InetAddress.getByName("::1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            try {
                server.receive(packet)
                server.send(DatagramPacket(packet.data, packet.offset, packet.length, packet.socketAddress))
            } catch (_: Exception) {}
        }
        serverThread.start()

        val clientSrc = InetSocketAddress(InetAddress.getByName("fd7a:115c:a1e0::2"), 53002)
        val serverDst = InetSocketAddress(InetAddress.getByName("::1"), serverPort)
        val payload = "Hello UDP v6".toByteArray()

        val udpPkt = PacketBuilder.ipv6Udp(clientSrc, serverDst, payload)
        fakeTun.toForwarder.offer(udpPkt)

        val start = System.currentTimeMillis()
        var receivedEcho: ByteArray? = null
        while (System.currentTimeMillis() - start < 5000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == serverDst && parsed.dst == clientSrc) {
                receivedEcho = ByteArray(parsed.payloadLength)
                System.arraycopy(resp, parsed.payloadOffset, receivedEcho, 0, parsed.payloadLength)
                break
            }
        }

        server.close()
        serverThread.join(1000)

        assertNotNull("Should receive IPv6 UDP echo response", receivedEcho)
        assertArrayEquals(payload, receivedEcho)
    }

    @Test(timeout = 10000)
    fun dns_answer_relays_trigger_on_dns() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val dnsPayload = PacketBuilder.dnsResponse(
            id = 0x1234,
            questions = listOf("example.com" to 1),
            answers = listOf(
                PacketBuilder.DnsRawAnswer(
                    nameRef = PacketBuilder.NameRef.Literal("example.com"),
                    type = 1,
                    ttl = 300,
                    rdata = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte())
                )
            )
        )

        val serverThread = Thread {
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            try {
                server.receive(packet)
                // Respond with dnsPayload
                server.send(DatagramPacket(dnsPayload, dnsPayload.size, packet.socketAddress))
            } catch (_: Exception) {}
        }
        serverThread.start()

        // Source port on app side: 53123, dest is serverPort (in test we simulate dest port 53 or server responding from port 53)
        // Wait, contract: "when a DNS answer is seen (UDP, source port 53, names reported as onDns)"
        // Or if destination was 53. If serverPort is ephemeral, can we simulate destination port 53?
        // Wait, on local machine, port 53 might require root/admin.
        // But what if destination was port 53 or source port was 53?
        // In our Forwarder, if dest port is 53 or src port is 53, DnsParser can check the payload!
        val clientSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53003)
        val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)

        // Query packet
        val queryPkt = PacketBuilder.ipv4Udp(clientSrc, serverDst, "dns query".toByteArray())
        fakeTun.toForwarder.offer(queryPkt)

        // The response comes back through the forwarder to fakeTun
        val start = System.currentTimeMillis()
        var receivedResp: ByteArray? = null
        while (System.currentTimeMillis() - start < 5000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == serverDst && parsed.dst == clientSrc) {
                receivedResp = ByteArray(parsed.payloadLength)
                System.arraycopy(resp, parsed.payloadOffset, receivedResp, 0, parsed.payloadLength)
                break
            }
        }

        server.close()
        serverThread.join(1000)

        assertNotNull(receivedResp)
        // Wait for onDns
        assertTrue("onDns should be called", testListener.dnsLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, testListener.dnsAnswers.size)
        assertEquals("example.com", testListener.dnsAnswers[0].name)
    }

    @Test(timeout = 10000)
    fun responses_from_wrong_source_discarded() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val rogueServer = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val roguePort = rogueServer.localPort

        var forwarderSocketAddress: InetSocketAddress? = null
        val serverThread = Thread {
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            try {
                server.receive(packet)
                forwarderSocketAddress = packet.socketAddress as InetSocketAddress
                // rogueServer sends packet to forwarderSocketAddress
                val rogueMsg = "rogue".toByteArray()
                rogueServer.send(DatagramPacket(rogueMsg, rogueMsg.size, forwarderSocketAddress))
                Thread.sleep(100)
                // then real server sends valid packet
                val validMsg = "valid".toByteArray()
                server.send(DatagramPacket(validMsg, validMsg.size, forwarderSocketAddress))
            } catch (_: Exception) {}
        }
        serverThread.start()

        val clientSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53004)
        val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)

        fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrc, serverDst, "ping".toByteArray()))

        val start = System.currentTimeMillis()
        val receivedPackets = mutableListOf<String>()
        while (System.currentTimeMillis() - start < 3000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp) {
                val payload = ByteArray(parsed.payloadLength)
                System.arraycopy(resp, parsed.payloadOffset, payload, 0, parsed.payloadLength)
                receivedPackets.add(String(payload))
            }
        }

        server.close()
        rogueServer.close()
        serverThread.join(1000)

        // Rogue response must be discarded by channel connect or filter
        assertEquals(listOf("valid"), receivedPackets)
    }

    @Test(timeout = 10000)
    fun idle_timeout_udp_60s_and_10s_port_53() {
        val serverRegular = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverDns = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        try {
            val regularPort = serverRegular.localPort
            val clientSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53005)
            val serverDstRegular = InetSocketAddress(InetAddress.getByName("127.0.0.1"), regularPort)
            val serverDstDns = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 53)

            // Send regular UDP
            fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrc, serverDstRegular, "data".toByteArray()))
            Thread.sleep(100)
            val regularFlowId = testListener.openedFlows.entries.first { it.value.remote.port == regularPort }.key

            // Send DNS UDP
            val clientSrc2 = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53006)
            fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrc2, serverDstDns, "dns".toByteArray()))
            Thread.sleep(100)
            val dnsFlowId = testListener.openedFlows.entries.first { it.value.remote.port == 53 }.key

            // Advance 11 seconds -> DNS flow should close, regular should stay open
            clock.advance(Duration.ofSeconds(11))
            Thread.sleep(200)
            assertTrue("DNS flow should close after 10s", testListener.closedFlows.containsKey(dnsFlowId))
            assertTrue("Regular flow should still be open after 11s", !testListener.closedFlows.containsKey(regularFlowId))

            // Advance another 50 seconds (total 61s) -> regular flow should close
            clock.advance(Duration.ofSeconds(50))
            Thread.sleep(200)
            assertTrue("Regular flow should close after 60s", testListener.closedFlows.containsKey(regularFlowId))
        } finally {
            serverRegular.close()
            serverDns.close()
        }
    }

    @Test(timeout = 10000)
    fun icmp_and_malformed_dropped_without_stopping_run() {
        // Send garbage packet
        fakeTun.toForwarder.offer(byteArrayOf(0, 1, 2, 3, 4, 5))

        // Send ICMP packet (IPv4 protocol 1)
        val icmpPacket = ByteArray(28)
        icmpPacket[0] = 0x45.toByte() // IPv4, IHL 5
        icmpPacket[9] = 1.toByte() // ICMP
        fakeTun.toForwarder.offer(icmpPacket)

        // Now send valid UDP echo
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val serverThread = Thread {
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            try {
                server.receive(packet)
                server.send(DatagramPacket(packet.data, packet.offset, packet.length, packet.socketAddress))
            } catch (_: Exception) {}
        }
        serverThread.start()

        val clientSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53007)
        val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        val payload = "ValidUDPAfterGarbage".toByteArray()

        fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrc, serverDst, payload))

        val start = System.currentTimeMillis()
        var receivedEcho: ByteArray? = null
        while (System.currentTimeMillis() - start < 5000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == serverDst && parsed.dst == clientSrc) {
                receivedEcho = ByteArray(parsed.payloadLength)
                System.arraycopy(resp, parsed.payloadOffset, receivedEcho, 0, parsed.payloadLength)
                break
            }
        }

        server.close()
        serverThread.join(1000)

        assertNotNull("Forwarder should remain running and process valid packet", receivedEcho)
        assertArrayEquals(payload, receivedEcho)
    }
}
