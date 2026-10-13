package io.github.khaledbahaaeldin.emberbyte.lens.core

import com.sun.management.ThreadMXBean
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ForwarderQ2Test {

    private lateinit var fakeTun: FakeTun
    private lateinit var forwarder: Forwarder
    private lateinit var forwarderThread: Thread
    private lateinit var testListener: TestFlowListener
    private var clock = TestClock()
    private val parser = PacketParser()

    class TestClock(var currentInstant: Instant = Instant.parse("2026-10-11T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = currentInstant
        fun advance(duration: Duration) {
            currentInstant = currentInstant.plus(duration)
        }
    }

    class TestFlowListener : FlowListener {
        val openedFlows = ConcurrentHashMap<Long, FlowInfo>()
        val closedFlows = ConcurrentHashMap<Long, AtomicInteger>()
        val names = ConcurrentHashMap<Long, MutableList<String>>()
        val dnsAnswers = java.util.concurrent.CopyOnWriteArrayList<DnsAnswer>()
        val dnsLatch = CountDownLatch(1)

        override fun onFlowOpened(flow: FlowInfo) {
            openedFlows[flow.flowId] = flow
        }

        override fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long) {}

        override fun onName(flowId: Long, name: String) {
            names.computeIfAbsent(flowId) { java.util.concurrent.CopyOnWriteArrayList() }.add(name)
        }

        override fun onDns(answers: List<DnsAnswer>) {
            dnsAnswers.addAll(answers)
            dnsLatch.countDown()
        }

        override fun onFlowClosed(flowId: Long) {
            closedFlows.computeIfAbsent(flowId) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    private val defaultProtector = object : SocketProtector {
        override fun protect(socket: Socket): Boolean = true
        override fun protect(socket: DatagramSocket): Boolean = true
        override fun protect(channel: SelectableChannel): Boolean = true
    }

    private val ownerResolver = object : OwnerResolver {
        override fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int = 10042
    }

    @Before
    fun setUp() {
        clock = TestClock()
        fakeTun = FakeTun()
        testListener = TestFlowListener()
        forwarder = Forwarder(
            tun = fakeTun,
            protector = defaultProtector,
            owner = ownerResolver,
            listener = testListener,
            clock = clock,
            maxFlows = 1024,
            mtu = 1400
        )
        forwarderThread = Thread({ forwarder.run() }, "Forwarder-Q2-Thread")
        forwarderThread.start()
    }

    @After
    fun tearDown() {
        forwarder.stop()
        fakeTun.close()
        forwarderThread.join(2000)
    }

    // ---------------------------------------------------------------------------------------------
    // Test 1: SNI over the first TLS record
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 15000)
    fun test1_sniOverFirstTlsRecord_splitAcrossSegmentsAndOutOfOrder() {
        // Echo / receiver server
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val receivedAtServer = java.io.ByteArrayOutputStream()
        val serverDoneLatch = CountDownLatch(1)

        val serverThread = Thread {
            try {
                val client = server.accept()
                val input = client.getInputStream()
                val buf = ByteArray(2048)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    receivedAtServer.write(buf, 0, read)
                    if (receivedAtServer.size() >= 1800) {
                        break
                    }
                }
                client.close()
            } catch (_: Exception) {} finally {
                serverDoneLatch.countDown()
                server.close()
            }
        }
        serverThread.start()

        // Build a ~1.8 KB ClientHello
        val expectedHost = "secure.example.org"
        val padding = ByteArray(1700) { 0x42.toByte() }
        val dummyExt = 0x0015 to padding // padding extension
        val clientHelloRecord = PacketBuilder.clientHello(serverName = expectedHost, extensions = listOf(dummyExt))
        assertTrue("ClientHello must be around 1.8 KB, actual: ${clientHelloRecord.size}", clientHelloRecord.size in 1750..2000)

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 51001),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue("Client handshake should succeed", sim.connect())
            val flowId = testListener.openedFlows.entries.first { it.value.local.port == 51001 }.key

            // Split into 2 segments: seg1 (1000 bytes) and seg2 (remaining bytes)
            val seg1 = clientHelloRecord.copyOfRange(0, 1000)
            val seg2 = clientHelloRecord.copyOfRange(1000, clientHelloRecord.size)

            // Send seg2 first (out of order arrival!)
            val baseSeq = sim.seq
            sim.sendRawPacket(
                flags = TcpFlags.ACK or TcpFlags.PSH,
                seqNum = (baseSeq + 1000) and 0xFFFFFFFFL,
                ackNum = sim.ack,
                payload = seg2
            )
            Thread.sleep(50)

            // Name should NOT be reported yet because seg1 was missing!
            assertEquals("Name should not be reported before first segment arrives", null, testListener.names[flowId])

            // Now send seg1 (completes the record)
            sim.sendRawPacket(
                flags = TcpFlags.ACK or TcpFlags.PSH,
                seqNum = baseSeq,
                ackNum = sim.ack,
                payload = seg1
            )
            sim.seq = (baseSeq + clientHelloRecord.size) and 0xFFFFFFFFL

            // Wait for server to receive
            serverDoneLatch.await(5, TimeUnit.SECONDS)

            // Verify name was reported exactly once
            val reportedNames = testListener.names[flowId]
            assertNotNull("SNI must be reported for split ClientHello", reportedNames)
            assertEquals("SNI must be reported exactly once", listOf(expectedHost), reportedNames)

            // Verify SHA-256 matches: buffered bytes forwarded unchanged and in order
            val expectedSha = MessageDigest.getInstance("SHA-256").digest(clientHelloRecord)
            val actualSha = MessageDigest.getInstance("SHA-256").digest(receivedAtServer.toByteArray())
            assertArrayEquals("Server received stream must match SHA-256 of ClientHello", expectedSha, actualSha)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }

        // Sub-case B: ClientHello split across 3 segments
        val server3 = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort3 = server3.localPort
        val serverDone3 = CountDownLatch(1)
        val serverThread3 = Thread {
            try {
                val client = server3.accept()
                val input = client.getInputStream()
                val buf = ByteArray(2048)
                while (input.read(buf) != -1) {
                    // drain
                }
                client.close()
            } catch (_: Exception) {} finally {
                serverDone3.countDown()
                server3.close()
            }
        }
        serverThread3.start()

        val sim3 = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 51002),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort3)
        )
        try {
            assertTrue(sim3.connect())
            val flowId3 = testListener.openedFlows.entries.first { it.value.local.port == 51002 }.key

            val segPart1 = clientHelloRecord.copyOfRange(0, 600)
            val segPart2 = clientHelloRecord.copyOfRange(600, 1200)
            val segPart3 = clientHelloRecord.copyOfRange(1200, clientHelloRecord.size)

            sim3.sendRawPacket(flags = TcpFlags.ACK, seqNum = sim3.seq, ackNum = sim3.ack, payload = segPart1)
            sim3.seq = (sim3.seq + 600) and 0xFFFFFFFFL
            Thread.sleep(20)
            sim3.sendRawPacket(flags = TcpFlags.ACK, seqNum = sim3.seq, ackNum = sim3.ack, payload = segPart2)
            sim3.seq = (sim3.seq + 600) and 0xFFFFFFFFL
            Thread.sleep(20)
            sim3.sendRawPacket(flags = TcpFlags.ACK or TcpFlags.PSH, seqNum = sim3.seq, ackNum = sim3.ack, payload = segPart3)
            sim3.seq = (sim3.seq + segPart3.size) and 0xFFFFFFFFL

            val deadline = System.currentTimeMillis() + 3000
            while (testListener.names[flowId3] == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(listOf(expectedHost), testListener.names[flowId3])
        } finally {
            sim3.stop()
            serverThread3.join(1000)
        }

        // Sub-case C: Non-TLS first record (HTTP) reports nothing and stops buffering
        val serverHttp = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPortHttp = serverHttp.localPort
        val httpReceived = java.io.ByteArrayOutputStream()
        val serverHttpLatch = CountDownLatch(1)
        val serverThreadHttp = Thread {
            try {
                val client = serverHttp.accept()
                val input = client.getInputStream()
                val buf = ByteArray(1024)
                var r: Int
                while (input.read(buf).also { r = it } != -1) {
                    httpReceived.write(buf, 0, r)
                    if (httpReceived.size() >= 30) break
                }
                client.close()
            } catch (_: Exception) {} finally {
                serverHttpLatch.countDown()
                serverHttp.close()
            }
        }
        serverThreadHttp.start()

        val simHttp = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 51003),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPortHttp)
        )
        try {
            assertTrue(simHttp.connect())
            val flowIdHttp = testListener.openedFlows.entries.first { it.value.local.port == 51003 }.key
            val httpPayload = "GET /index.html HTTP/1.1\r\nHost: example.org\r\n\r\n".toByteArray()
            simHttp.sendData(httpPayload)
            serverHttpLatch.await(3, TimeUnit.SECONDS)

            assertEquals("Non-TLS traffic must NOT report server name", null, testListener.names[flowIdHttp])
            assertArrayEquals("HTTP payload forwarded unchanged", httpPayload, httpReceived.toByteArray())
        } finally {
            simHttp.stop()
            serverThreadHttp.join(1000)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Test 2: UDP large replies
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 10000)
    fun test2_udpLargeReplies_exceedingTunMtu_droppedAndCounted_flowStaysAlive() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        val clientSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 52001)

        var forwarderClientAddr: SocketAddress? = null
        val serverThread = Thread {
            val buf = ByteArray(65535)
            val packet = DatagramPacket(buf, buf.size)
            try {
                server.receive(packet)
                forwarderClientAddr = packet.socketAddress

                // 1) Send 1,500 byte reply (exceeds TUN MTU 1400 - 28 = 1372)
                val reply1500 = ByteArray(1500) { 1 }
                server.send(DatagramPacket(reply1500, reply1500.size, forwarderClientAddr))
                Thread.sleep(100)

                // 2) Send 4,000 byte reply
                val reply4000 = ByteArray(4000) { 2 }
                server.send(DatagramPacket(reply4000, reply4000.size, forwarderClientAddr))
                Thread.sleep(100)

                // 3) Send 60,000 byte reply
                val reply60000 = ByteArray(60000) { 3 }
                server.send(DatagramPacket(reply60000, reply60000.size, forwarderClientAddr))
                Thread.sleep(100)

                // 4) Send normal reply (500 bytes, fits in TUN MTU)
                val normalReply = "normal-reply-data".toByteArray()
                server.send(DatagramPacket(normalReply, normalReply.size, forwarderClientAddr))
            } catch (_: Exception) {}
        }
        serverThread.start()

        // Send initial UDP packet from client to trigger flow
        fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrc, serverDst, "ping".toByteArray()))

        val start = System.currentTimeMillis()
        var receivedNormal: ByteArray? = null
        while (System.currentTimeMillis() - start < 5000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == serverDst && parsed.dst == clientSrc) {
                val p = ByteArray(parsed.payloadLength)
                System.arraycopy(resp, parsed.payloadOffset, p, 0, parsed.payloadLength)
                receivedNormal = p
                break
            }
        }

        server.close()
        serverThread.join(1000)

        // Oversized datagrams counter must be 3
        assertEquals("Three oversized datagrams must be dropped and counted", 3L, forwarder.oversizedDatagrams.get())

        // Normal reply must be delivered
        assertNotNull("Normal reply must be delivered after oversized replies", receivedNormal)
        assertEquals("normal-reply-data", String(receivedNormal!!))

        // Flow must remain alive
        val flowId = testListener.openedFlows.entries.first { it.value.local.port == 52001 }.key
        assertNull("Flow must stay alive despite oversized replies", testListener.closedFlows[flowId])
    }

    // ---------------------------------------------------------------------------------------------
    // Test 3: UDP parsed as DNS ONLY on port 53
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 10000)
    fun test3_udpParsedAsDnsOnlyOnPort53() {
        val dnsPayload = PacketBuilder.dnsResponse(
            id = 0x55aa,
            questions = listOf("domain.com" to 1),
            answers = listOf(
                PacketBuilder.DnsRawAnswer(
                    nameRef = PacketBuilder.NameRef.Literal("domain.com"),
                    type = 1,
                    ttl = 60,
                    rdata = byteArrayOf(1, 2, 3, 4)
                )
            )
        )

        // Case A: Port 5353 (mDNS) carrying valid DNS payload
        val server5353 = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port5353 = server5353.localPort
        val clientSrcA = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53531)
        val dst5353 = InetSocketAddress(InetAddress.getByName("127.0.0.1"), port5353)

        val th5353 = Thread {
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            try {
                server5353.receive(p)
                server5353.send(DatagramPacket(dnsPayload, dnsPayload.size, p.socketAddress))
            } catch (_: Exception) {}
        }
        th5353.start()

        fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrcA, dst5353, "query".toByteArray()))
        // Wait for reply packet back at TUN
        val startA = System.currentTimeMillis()
        while (System.currentTimeMillis() - startA < 2000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == dst5353) break
        }
        server5353.close()
        th5353.join(500)

        assertEquals("Port != 53 must NOT trigger onDns", 0, testListener.dnsAnswers.size)

        // Case B: Port 8000 carrying valid DNS payload
        val server8000 = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port8000 = server8000.localPort
        val clientSrcB = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53532)
        val dst8000 = InetSocketAddress(InetAddress.getByName("127.0.0.1"), port8000)

        val th8000 = Thread {
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            try {
                server8000.receive(p)
                server8000.send(DatagramPacket(dnsPayload, dnsPayload.size, p.socketAddress))
            } catch (_: Exception) {}
        }
        th8000.start()

        fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrcB, dst8000, "query".toByteArray()))
        val startB = System.currentTimeMillis()
        while (System.currentTimeMillis() - startB < 2000) {
            val resp = fakeTun.fromForwarder.poll(100, TimeUnit.MILLISECONDS) ?: continue
            val parsed = parser.parse(ByteBuffer.wrap(resp), resp.size)
            if (parsed is ParsedPacket.Udp && parsed.src == dst8000) break
        }
        server8000.close()
        th8000.join(500)

        assertEquals("Port 8000 must NOT trigger onDns", 0, testListener.dnsAnswers.size)

        // Case C: Port 53 reply carrying valid DNS payload
        val clientSrcC = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 53533)
        val dst53 = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 53)

        // Trigger flow creation
        fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(clientSrcC, dst53, "query".toByteArray()))
        Thread.sleep(100)
        val flow53 = testListener.openedFlows.entries.first { it.value.local.port == 53533 }.value

        // Feed dnsPayload into the UDP flow directly via reflection or packet
        val key = FlowKey(17, clientSrcC, dst53)
        val internalField = Forwarder::class.java.getDeclaredField("flowTable")
        internalField.isAccessible = true
        val table = internalField.get(forwarder) as FlowTable
        var udpFlow: UdpFlow? = null
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline && udpFlow == null) {
            udpFlow = table.getUdp(key)
            if (udpFlow == null) Thread.sleep(20)
        }
        assertNotNull("UdpFlow must be present in flow table", udpFlow)
        val nonNullUdpFlow = udpFlow!!

        // Simulate readable event on udpFlow's socket by injecting payload
        // Using onSocketReadable logic: write packet to channel or simulate onSocketReadable
        val dc = udpFlow.channel!!
        // Since port 53 is not bound on localhost, we can send to dc using an ephemeral datagram socket
        val sender = DatagramSocket()
        sender.send(DatagramPacket(dnsPayload, dnsPayload.size, dc.socket().localSocketAddress))
        sender.close()

        assertTrue("Port 53 reply MUST trigger onDns", testListener.dnsLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, testListener.dnsAnswers.size)
        assertEquals("domain.com", testListener.dnsAnswers[0].name)
    }

    // ---------------------------------------------------------------------------------------------
    // Test 4: Owner cache bounded at 4096 and removes on close
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 20000)
    fun test4_ownerCache_boundedAt4096_andRemovesOnFlowClosed() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)

        val internalField = Forwarder::class.java.getDeclaredField("flowTable")
        internalField.isAccessible = true
        val table = internalField.get(forwarder) as FlowTable
        val testSelector = Selector.open()
        try {
            // Verify LRU bounding when entries exceed 4,096
            for (i in 1..10_000) {
                val src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), (i % 60000) + 1024)
                val dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), (i / 60000) + 5000)
                forwarder.registerFlowForTest(FlowKey(17, src, dst), 10042)
            }
            assertEquals("Cache size must be bounded at exactly 4096 entries", 4096, forwarder.uidCacheSize)

            // 100,000 short flows: registered, opened, then closed -> removes entry from owner cache
            for (i in 1..100_000) {
                val src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), (i % 60000) + 1024)
                val dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), (i / 60000) + 5000)
                val key = FlowKey(17, src, dst)
                val flowId = table.generateFlowId()

                forwarder.registerFlowForTest(key, 10042)
                forwarder.flowListener.onFlowOpened(FlowInfo(flowId, 10042, 17, src, dst, clock.instant()))

                assertTrue("Cache size must never exceed 4096 entries (was ${forwarder.uidCacheSize})", forwarder.uidCacheSize <= 4096)

                // Close flow (short flow) -> removes entry from owner cache
                forwarder.flowListener.onFlowClosed(flowId)
            }

            // Real flows integration test: verify that closing a real UdpFlow removes its cache entry
            for (i in 1..5) {
                val realSrc = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 40000 + i)
                val realKey = FlowKey(17, realSrc, serverDst)
                val realFlowId = table.generateFlowId()
                forwarder.registerFlowForTest(realKey, 10042)
                val realFlow = UdpFlow(
                    flowId = realFlowId,
                    src = realSrc,
                    dst = serverDst,
                    ownerUid = 10042,
                    tun = fakeTun,
                    protector = defaultProtector,
                    listener = forwarder.flowListener,
                    clock = clock,
                    mtu = 1400,
                    selector = testSelector,
                    tunWriteLock = Any(),
                    oversizedDatagramsCounter = forwarder.oversizedDatagrams
                )
                val beforeClose = forwarder.uidCacheSize
                realFlow.close()
                assertEquals("Closing real UdpFlow must remove its entry from uidCache", beforeClose - 1, forwarder.uidCacheSize)
            }
        } finally {
            testSelector.close()
            server.close()
        }

        assertTrue("Cache size must stay bounded at <= 4096", forwarder.uidCacheSize <= 4096)
    }

    // ---------------------------------------------------------------------------------------------
    // Test 5: onFlowClosed exactly once for every flow in every close path
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 15000)
    fun test5_onFlowClosed_exactlyOnceInEveryClosePath() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    val s = server.accept()
                    s.close()
                }
            } catch (_: Exception) {}
        }
        serverThread.start()

        // 1. Normal close (TCP FIN)
        val sim1 = TcpClientSim(fakeTun, InetSocketAddress("10.7.0.2", 53001), InetSocketAddress("127.0.0.1", serverPort))
        assertTrue(sim1.connect())
        val flow1Id = testListener.openedFlows.entries.first { it.value.local.port == 53001 }.key
        sim1.sendFin()
        Thread.sleep(100)
        sim1.stop()

        // 2. RST from client
        val sim2 = TcpClientSim(fakeTun, InetSocketAddress("10.7.0.2", 53002), InetSocketAddress("127.0.0.1", serverPort))
        assertTrue(sim2.connect())
        val flow2Id = testListener.openedFlows.entries.first { it.value.local.port == 53002 }.key
        sim2.sendRst()
        Thread.sleep(100)
        sim2.stop()

        // 3. Idle timeout
        val sim3 = TcpClientSim(fakeTun, InetSocketAddress("10.7.0.2", 53003), InetSocketAddress("127.0.0.1", serverPort))
        assertTrue(sim3.connect())
        val flow3Id = testListener.openedFlows.entries.first { it.value.local.port == 53003 }.key
        clock.advance(Duration.ofSeconds(305)) // Exceeds 300s idle timeout
        Thread.sleep(200)
        sim3.stop()

        // 4. Eviction (exceed maxFlows)
        // Set up forwarder with maxFlows = 2
        val evictTun = FakeTun()
        val evictListener = TestFlowListener()
        val evictForwarder = Forwarder(
            tun = evictTun,
            protector = defaultProtector,
            owner = ownerResolver,
            listener = evictListener,
            clock = clock,
            maxFlows = 2,
            mtu = 1400
        )
        val evictThread = Thread({ evictForwarder.run() }, "Evict-Thread")
        evictThread.start()

        val udpServer = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val udpPort = udpServer.localPort
        val udpDst = InetSocketAddress("127.0.0.1", udpPort)

        evictTun.toForwarder.offer(PacketBuilder.ipv4Udp(InetSocketAddress("10.7.0.2", 60001), udpDst, "f1".toByteArray()))
        Thread.sleep(50)
        clock.advance(Duration.ofSeconds(1))
        evictTun.toForwarder.offer(PacketBuilder.ipv4Udp(InetSocketAddress("10.7.0.2", 60002), udpDst, "f2".toByteArray()))
        Thread.sleep(50)
        clock.advance(Duration.ofSeconds(1))
        // 3rd flow triggers eviction of f1
        evictTun.toForwarder.offer(PacketBuilder.ipv4Udp(InetSocketAddress("10.7.0.2", 60003), udpDst, "f3".toByteArray()))
        Thread.sleep(100)

        // 5. stop() on evictForwarder
        evictForwarder.stop()
        evictTun.close()
        evictThread.join(2000)
        udpServer.close()

        server.close()
        serverThread.join(500)

        // Trigger secondary close / stop on main forwarder flows to verify idempotency
        forwarder.stop()
        forwarderThread.join(1000)

        // Explicitly test close idempotence on a flow instance across multiple close invocations
        val multiCloseFlow = TcpFlow(
            flowId = 99999L,
            src = InetSocketAddress("10.7.0.2", 59999),
            dst = InetSocketAddress("127.0.0.1", 80),
            ownerUid = 10042,
            tun = fakeTun,
            protector = defaultProtector,
            listener = testListener,
            clock = clock,
            mtu = 1400,
            selector = Selector.open(),
            tunWriteLock = Any()
        )
        multiCloseFlow.close()
        multiCloseFlow.close()
        assertEquals("multiCloseFlow must be closed exactly once", 1, testListener.closedFlows[99999L]?.get())

        // Assert on main forwarder flows: closed exactly once
        for (fId in listOf(flow1Id, flow2Id, flow3Id)) {
            val count = testListener.closedFlows[fId]?.get() ?: 0
            assertEquals("Flow $fId must be closed exactly once, actual: $count", 1, count)
        }

        // Assert on evictForwarder flows: all opened flows closed exactly once
        for ((fId, _) in evictListener.openedFlows) {
            val count = evictListener.closedFlows[fId]?.get() ?: 0
            assertEquals("Evicted/stopped flow $fId must be closed exactly once, actual: $count", 1, count)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Test 6: protect() failing or throwing
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 10000)
    fun test6_protectFailingOrThrowing_closesWithRst_noConnectAttempted() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val serverAcceptedCount = AtomicInteger(0)
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    val s = server.accept()
                    serverAcceptedCount.incrementAndGet()
                    s.close()
                }
            } catch (_: Exception) {}
        }
        serverThread.start()

        val countingFactory = object : SocketChannelFactory {
            val channels = java.util.concurrent.CopyOnWriteArrayList<SocketChannel>()
            override fun open(): SocketChannel {
                val sc = SocketChannel.open()
                channels.add(sc)
                return sc
            }
        }

        var protectReturnValue = false
        var protectShouldThrow = false

        val testProtector = object : SocketProtector {
            override fun protect(socket: Socket): Boolean = protectReturnValue
            override fun protect(socket: DatagramSocket): Boolean = protectReturnValue
            override fun protect(channel: SelectableChannel): Boolean {
                if (protectShouldThrow) {
                    throw RuntimeException("Simulated protect() crash")
                }
                return protectReturnValue
            }
        }

        val protTun = FakeTun()
        val protListener = TestFlowListener()
        val protForwarder = Forwarder(
            tun = protTun,
            protector = testProtector,
            owner = ownerResolver,
            listener = protListener,
            clock = clock,
            maxFlows = 1024,
            mtu = 1400,
            channelFactory = countingFactory
        )
        val protThread = Thread({ protForwarder.run() }, "Prot-Thread")
        protThread.start()

        try {
            // Case A: protect() returns false
            protectReturnValue = false
            protectShouldThrow = false

            val simA = TcpClientSim(
                tun = protTun,
                src = InetSocketAddress("10.7.0.2", 54001),
                dst = InetSocketAddress("127.0.0.1", serverPort)
            )
            simA.start()
            simA.sendRawPacket(flags = TcpFlags.SYN, seqNum = 1000L, ackNum = 0L)

            // Expect RST or RST/ACK in response
            assertTrue("Client must receive RST when protect() returns false", simA.rstReceivedLatch.await(3, TimeUnit.SECONDS))
            assertEquals("protectFailures counter must increment", 1L, protForwarder.protectFailures.get())

            // Assert socket was NEVER connected
            val channelA = countingFactory.channels.firstOrNull()
            assertNotNull("Channel must have been opened", channelA)
            assertTrue("Channel must never have connected", !channelA!!.isConnected)
            assertTrue("Channel must never have initiated connection", !channelA.isConnectionPending)
            assertEquals("Server must never have accepted a connection", 0, serverAcceptedCount.get())
            simA.stop()

            // Case B: protect() throws exception
            protectShouldThrow = true

            val simB = TcpClientSim(
                tun = protTun,
                src = InetSocketAddress("10.7.0.2", 54002),
                dst = InetSocketAddress("127.0.0.1", serverPort)
            )
            simB.start()
            simB.sendRawPacket(flags = TcpFlags.SYN, seqNum = 2000L, ackNum = 0L)

            assertTrue("Client must receive RST when protect() throws", simB.rstReceivedLatch.await(3, TimeUnit.SECONDS))
            assertEquals("protectFailures counter must increment to 2", 2L, protForwarder.protectFailures.get())

            val channelB = countingFactory.channels.getOrNull(1)
            assertNotNull("Second channel must have been opened", channelB)
            assertTrue("Channel B must never have connected", !channelB!!.isConnected)
            assertTrue("Channel B must never have initiated connection", !channelB.isConnectionPending)
            assertEquals("Server must still have 0 accepted connections", 0, serverAcceptedCount.get())
            simB.stop()

            // Case C: Forwarder keeps running! Protect succeeds now -> connection works
            protectShouldThrow = false
            protectReturnValue = true

            val simC = TcpClientSim(
                tun = protTun,
                src = InetSocketAddress("10.7.0.2", 54003),
                dst = InetSocketAddress("127.0.0.1", serverPort)
            )
            assertTrue("Forwarder must still be running and accept subsequent connections", simC.connect())
            simC.stop()
        } finally {
            protForwarder.stop()
            protTun.close()
            protThread.join(2000)
            server.close()
            serverThread.join(500)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Test 7: Allocations <= 2x payload on network and TUN-reader threads
    // ---------------------------------------------------------------------------------------------
    @Test(timeout = 30000)
    fun test7_allocations_relaying50MiB_allocatesAtMost2xPayload() {
        val mxBean = ManagementFactory.getThreadMXBean()
        Assume.assumeTrue("ThreadMXBean must be Sun/HotSpot ThreadMXBean", mxBean is ThreadMXBean)
        val sunMxBean = mxBean as ThreadMXBean
        Assume.assumeTrue("Thread allocated memory must be supported", sunMxBean.isThreadAllocatedMemorySupported)

        // Echo server: reads data and writes it back immediately
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort
        val serverRunning = AtomicBoolean(true)
        val serverBytesRelayed = AtomicLong(0)
        val serverThread = Thread {
            try {
                val client = server.accept()
                val input = client.getInputStream()
                val output = client.getOutputStream()

                val queue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
                val EOF_MARKER = ByteArray(0)

                val writerThread = Thread {
                    try {
                        while (true) {
                            val chunk = queue.take()
                            if (chunk === EOF_MARKER) break
                            output.write(chunk)
                            output.flush()
                        }
                    } catch (_: Exception) {}
                }
                writerThread.start()

                val buf = ByteArray(65536)
                var r = 0
                while (serverRunning.get() && input.read(buf).also { r = it } != -1) {
                    serverBytesRelayed.addAndGet(r.toLong())
                    val chunk = ByteArray(r)
                    System.arraycopy(buf, 0, chunk, 0, r)
                    queue.put(chunk)
                }
                queue.put(EOF_MARKER)
                writerThread.join(2000)
                client.close()
            } catch (_: Exception) {} finally {
                server.close()
            }
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 55001),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())

            val readerThread = forwarder.tunReaderThreadInstance
            val netThread = forwarder.networkThreadInstance
            assertNotNull("tunReaderThread must be active", readerThread)
            assertNotNull("networkThread must be active", netThread)

            val readerId = readerThread!!.id
            val netId = netThread!!.id

            // Warm up with 64 KiB
            val warmup = ByteArray(65536) { 0x55.toByte() }
            sim.sendData(warmup)
            val warmupStart = System.currentTimeMillis()
            while (sim.receivedData.size() < warmup.size && System.currentTimeMillis() - warmupStart < 3000) {
                Thread.sleep(10)
            }
            sim.receivedData.reset()
            serverBytesRelayed.set(0)

            val startReader = sunMxBean.getThreadAllocatedBytes(readerId)
            val startNet = sunMxBean.getThreadAllocatedBytes(netId)
            val startAlloc = startReader + startNet

            // Transfer 50 MiB
            val totalBytes = 50 * 1024 * 1024 // 52,428,800 bytes
            val chunk = ByteArray(65536) { 0xAA.toByte() }
            var sent = 0
            while (sent < totalBytes) {
                val toSend = minOf(chunk.size, totalBytes - sent)
                val buf = if (toSend == chunk.size) chunk else chunk.copyOf(toSend)
                sim.sendData(buf)
                sent += toSend
                synchronized(sim.receivedData) {
                    sim.receivedData.reset()
                }
                if (sent % (1024 * 1024) == 0) {
                    Thread.sleep(5)
                }
            }

            // Wait for echo server to receive and echo back the 50 MiB
            val deadline = System.currentTimeMillis() + 15000
            while (serverBytesRelayed.get() < totalBytes && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals("Server must have received and relayed all 50 MiB", totalBytes.toLong(), serverBytesRelayed.get())

            val endReader = sunMxBean.getThreadAllocatedBytes(readerId)
            val endNet = sunMxBean.getThreadAllocatedBytes(netId)
            val readerAlloc = endReader - startReader
            val netAlloc = endNet - startNet
            val totalAllocatedOnThreads = readerAlloc + netAlloc
            println("ALLOCATIONS: total=$totalAllocatedOnThreads, readerAlloc=$readerAlloc, netAlloc=$netAlloc")

            val maxAllowedAllocations = 2L * totalBytes
            assertTrue(
                "Total allocations ($totalAllocatedOnThreads bytes) must be <= 2x payload ($maxAllowedAllocations bytes)",
                totalAllocatedOnThreads <= maxAllowedAllocations
            )
        } finally {
            serverRunning.set(false)
            sim.stop()
            serverThread.join(1000)
        }
    }
}
