package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SelectableChannel
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ForwarderTcpTest {

    private lateinit var fakeTun: FakeTun
    private lateinit var forwarder: Forwarder
    private lateinit var forwarderThread: Thread
    private lateinit var testListener: TestFlowListener
    private val clock = TestClock()

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
        val dnsAnswers = CopyOnWriteArrayListWrapper<DnsAnswer>()

        val onNameLatch = CountDownLatch(1)
        val onOpenLatch = CountDownLatch(1)

        override fun onFlowOpened(flow: FlowInfo) {
            openedFlows[flow.flowId] = flow
            onOpenLatch.countDown()
        }

        override fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long) {
            totalRx.addAndGet(rxBytes)
            totalTx.addAndGet(txBytes)
        }

        override fun onName(flowId: Long, name: String) {
            names[flowId] = name
            onNameLatch.countDown()
        }

        override fun onDns(answers: List<DnsAnswer>) {
            dnsAnswers.addAll(answers)
        }

        override fun onFlowClosed(flowId: Long) {
            closedFlows[flowId] = true
        }
    }

    class CopyOnWriteArrayListWrapper<T> {
        private val list = java.util.concurrent.CopyOnWriteArrayList<T>()
        fun addAll(elements: Collection<T>) = list.addAll(elements)
        fun toList(): List<T> = list.toList()
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
        clock.currentInstant = Instant.parse("2026-10-11T12:00:00Z")
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
        forwarderThread = Thread({ forwarder.run() }, "Forwarder-Test-Thread")
        forwarderThread.start()
    }

    @After
    fun tearDown() {
        forwarder.stop()
        fakeTun.close()
        forwarderThread.join(2000)
    }

    @Test(timeout = 10000)
    fun handshake_and_echo_data() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val buf = ByteArray(1024)
            val read = input.read(buf)
            if (read > 0) {
                output.write(buf, 0, read)
                output.flush()
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54321),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue("Handshake should complete", sim.connect())
            val message = "Hello Emberbyte Lens!".toByteArray()
            sim.sendData(message)

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < message.size && System.currentTimeMillis() - start < 5000) {
                Thread.sleep(20)
            }
            assertArrayEquals(message, sim.receivedData.toByteArray())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 30000)
    fun transfer_10mb_byte_identical() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val size = 10 * 1024 * 1024 // 10 MB
        val random = Random(12345)
        val uploadBytes = ByteArray(size).also { random.nextBytes(it) }
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(uploadBytes)

        val serverDigestRef = AtomicReference<ByteArray>()
        val serverThread = Thread {
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

            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(65536)
            var totalRead = 0
            while (totalRead < size) {
                val r = input.read(buf, 0, minOf(buf.size, size - totalRead))
                if (r <= 0) break
                digest.update(buf, 0, r)
                val chunk = ByteArray(r)
                System.arraycopy(buf, 0, chunk, 0, r)
                queue.put(chunk)
                totalRead += r
            }
            queue.put(EOF_MARKER)
            writerThread.join()

            serverDigestRef.set(digest.digest())
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54322),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue("Handshake should complete", sim.connect())
            // Stream chunks
            var sent = 0
            val chunkSize = 32768
            while (sent < size) {
                val chunkLen = minOf(chunkSize, size - sent)
                val chunk = ByteArray(chunkLen)
                System.arraycopy(uploadBytes, sent, chunk, 0, chunkLen)
                sim.sendData(chunk)
                sent += chunkLen
                if (sent % (1024 * 1024) == 0) {
                    Thread.sleep(10)
                }
            }

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < size && System.currentTimeMillis() - start < 25000) {
                Thread.sleep(20)
            }
            assertEquals(size, sim.receivedData.size())
            val clientSha = MessageDigest.getInstance("SHA-256").digest(sim.receivedData.toByteArray())
            assertArrayEquals(expectedSha, clientSha)
            assertArrayEquals(expectedSha, serverDigestRef.get())
        } finally {
            sim.stop()
            serverThread.join(2000)
        }
    }

    @Test(timeout = 30000)
    fun transfer_2mb_with_5_percent_drops() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val size = 2 * 1024 * 1024 // 2 MB
        val random = Random(54321)
        val uploadBytes = ByteArray(size).also { random.nextBytes(it) }
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(uploadBytes)

        val serverThread = Thread {
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

            val buf = ByteArray(32768)
            var totalRead = 0
            while (totalRead < size) {
                val r = input.read(buf, 0, minOf(buf.size, size - totalRead))
                if (r <= 0) break
                val chunk = ByteArray(r)
                System.arraycopy(buf, 0, chunk, 0, r)
                queue.put(chunk)
                totalRead += r
            }
            queue.put(EOF_MARKER)
            writerThread.join()

            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54323),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort),
            dropRate = 0.05,
            random = Random(999)
        )

        try {
            assertTrue("Handshake should complete", sim.connect())
            var sent = 0
            val chunkSize = 16384
            while (sent < size) {
                val chunkLen = minOf(chunkSize, size - sent)
                val chunk = ByteArray(chunkLen)
                System.arraycopy(uploadBytes, sent, chunk, 0, chunkLen)
                sim.sendData(chunk)
                sent += chunkLen
                Thread.sleep(5)
            }

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < size && System.currentTimeMillis() - start < 22000) {
                Thread.sleep(50)
            }
            assertEquals(size, sim.receivedData.size())
            val clientSha = MessageDigest.getInstance("SHA-256").digest(sim.receivedData.toByteArray())
            assertArrayEquals(expectedSha, clientSha)
        } finally {
            sim.stop()
            serverThread.join(2000)
        }
    }

    @Test(timeout = 10000)
    fun half_close_client_fin() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverSawEof = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            // Read until EOF
            val buf = ByteArray(1024)
            while (input.read(buf) != -1) {}
            serverSawEof.countDown()
            // Server still sends after client FIN
            output.write("server response after fin".toByteArray())
            output.flush()
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54324),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            sim.sendFin()
            assertTrue(serverSawEof.await(5, TimeUnit.SECONDS))
            assertTrue(sim.finReceivedLatch.await(5, TimeUnit.SECONDS))
            assertEquals("server response after fin", String(sim.receivedData.toByteArray()))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun server_closes_first() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val output = client.getOutputStream()
            output.write("farewell".toByteArray())
            output.flush()
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54325),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            assertTrue(sim.finReceivedLatch.await(5, TimeUnit.SECONDS))
            assertEquals("farewell", String(sim.receivedData.toByteArray()))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun server_rst_becomes_rst_at_client() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            client.setSoLinger(true, 0) // Causes hard RST on close
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54326),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            assertTrue(sim.rstReceivedLatch.await(5, TimeUnit.SECONDS))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun connect_to_closed_port_returns_rst_ack() {
        // Find an unused closed port
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val closedPort = server.localPort
        server.close()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54327),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), closedPort)
        )

        try {
            sim.start()
            sim.sendRawPacket(flags = TcpFlags.SYN, seqNum = 100L, ackNum = 0L)
            assertTrue("Should receive RST", sim.rstReceivedLatch.await(5, TimeUnit.SECONDS))
        } finally {
            sim.stop()
        }
    }

    @Test(timeout = 10000)
    fun sequence_wrap_around_0xFFFFFF00() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val buf = ByteArray(1024)
            var total = 0
            while (total < 1000) {
                val r = input.read(buf)
                if (r <= 0) break
                output.write(buf, 0, r)
                output.flush()
                total += r
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54328),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort),
            seq = 0xFFFFFF00L
        )

        try {
            assertTrue(sim.connect())
            val data = ByteArray(1000) { (it and 0xFF).toByte() }
            sim.sendData(data)

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < 1000 && System.currentTimeMillis() - start < 5000) {
                Thread.sleep(20)
            }
            assertArrayEquals(data, sim.receivedData.toByteArray())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun duplicate_segment_is_re_acked_and_not_delivered_twice() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val receivedAtServer = ByteArray(1024)
        val serverReadCount = AtomicLong(0)
        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val r = input.read(receivedAtServer)
            if (r > 0) serverReadCount.set(r.toLong())
            Thread.sleep(200)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54329),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val chunk = "HelloDup".toByteArray()
            val currentSeq = sim.seq
            sim.sendRawPacket(flags = TcpFlags.ACK or TcpFlags.PSH, seqNum = currentSeq, ackNum = sim.ack, payload = chunk)
            Thread.sleep(50)
            // Send duplicate segment with same seq
            sim.sendRawPacket(flags = TcpFlags.ACK or TcpFlags.PSH, seqNum = currentSeq, ackNum = sim.ack, payload = chunk)
            Thread.sleep(300)
            assertEquals("Server should only have received chunk once", chunk.size.toLong(), serverReadCount.get())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun out_of_order_segment_not_delivered_early() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverBuffer = java.io.ByteArrayOutputStream()
        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val buf = ByteArray(1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                synchronized(serverBuffer) { serverBuffer.write(buf, 0, r) }
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54330),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val chunk1 = "FIRST_".toByteArray()
            val chunk2 = "SECOND".toByteArray()

            val seq1 = sim.seq
            val seq2 = (seq1 + chunk1.size) and 0xFFFFFFFFL

            // Send chunk2 first!
            sim.sendRawPacket(flags = TcpFlags.ACK or TcpFlags.PSH, seqNum = seq2, ackNum = sim.ack, payload = chunk2)
            Thread.sleep(100)
            // Server should not have received anything yet
            assertEquals(0, synchronized(serverBuffer) { serverBuffer.size() })

            // Now send chunk1
            sim.sendRawPacket(flags = TcpFlags.ACK or TcpFlags.PSH, seqNum = seq1, ackNum = sim.ack, payload = chunk1)
            Thread.sleep(300)
            sim.sendFin()

            val start = System.currentTimeMillis()
            while (synchronized(serverBuffer) { serverBuffer.size() } < 12 && System.currentTimeMillis() - start < 3000) {
                Thread.sleep(50)
            }
            assertEquals("FIRST_SECOND", synchronized(serverBuffer) { serverBuffer.toString() })
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun client_window_zero_pauses_server_data() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val windowZeroLatch = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            val output = client.getOutputStream()
            output.write("part1".toByteArray())
            output.flush()
            windowZeroLatch.await(5, TimeUnit.SECONDS)
            output.write("part2".toByteArray())
            output.flush()
            Thread.sleep(500)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54331),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val startPart1 = System.currentTimeMillis()
            while (sim.receivedData.size() < 5 && System.currentTimeMillis() - startPart1 < 3000) {
                Thread.sleep(20)
            }
            // Advertise window 0
            sim.updateWindow(0)
            Thread.sleep(100)
            // Signal server to write part2
            windowZeroLatch.countDown()
            Thread.sleep(400)

            // Client should only have received part1 (5 bytes)
            val sizeBefore = sim.receivedData.size()
            assertEquals("No data beyond part1 received while window is 0", 5, sizeBefore)
            val packetsAtWindow0 = fakeTun.fromForwarder.size
            Thread.sleep(300)
            val newPacketsDuringWindow0 = fakeTun.fromForwarder.toList().drop(packetsAtWindow0).count { pkt ->
                val parsed = PacketParser().parse(ByteBuffer.wrap(pkt), pkt.size)
                parsed is ParsedPacket.Tcp && parsed.src == sim.dst && parsed.dst == sim.src && parsed.payloadLength > 1
            }
            assertEquals("Assert no regular data packets sent while window is 0", 0, newPacketsDuringWindow0)

            // Open window
            sim.updateWindow(65535)
            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < 10 && System.currentTimeMillis() - start < 3000) {
                Thread.sleep(50)
            }
            assertEquals("part1part2", sim.receivedData.toString())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun sni_triggers_on_name() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val buf = ByteArray(1024)
            input.read(buf)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54332),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val tlsRecord = PacketBuilder.clientHello("secure.example.com")
            sim.sendData(tlsRecord)

            assertTrue("onName should be called", testListener.onNameLatch.await(5, TimeUnit.SECONDS))
            assertTrue(testListener.names.values.contains("secure.example.com"))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun on_bytes_equals_payload_sizes() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val buf = ByteArray(1024)
            val r = input.read(buf)
            if (r > 0) {
                output.write(buf, 0, r)
                output.flush()
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54333),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val payload = "Testing payload byte counting".toByteArray()
            sim.sendData(payload)

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < payload.size && System.currentTimeMillis() - start < 3000) {
                Thread.sleep(20)
            }
            assertEquals(payload.size.toLong(), testListener.totalTx.get())
            assertEquals(payload.size.toLong(), testListener.totalRx.get())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun idle_timeout_closes_flow() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            Thread.sleep(2000)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54334),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val flowId = testListener.openedFlows.keys.firstOrNull()
            assertNotNull(flowId)

            // Advance clock by 5 minutes and 1 second
            clock.advance(Duration.ofSeconds(301))

            val start = System.currentTimeMillis()
            while (!testListener.closedFlows.containsKey(flowId) && System.currentTimeMillis() - start < 5000) {
                clock.advance(Duration.ofSeconds(60))
                Thread.sleep(50)
            }
            assertTrue("Flow should be closed due to idle timeout", testListener.closedFlows.containsKey(flowId))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun ipv6_tcp_flow() {
        val ipv6Supported = try {
            val ss = ServerSocket(0, 50, InetAddress.getByName("::1"))
            ss.close()
            true
        } catch (_: Exception) {
            false
        }
        Assume.assumeTrue("IPv6 loopback not supported on this host", ipv6Supported)

        val server = ServerSocket(0, 50, InetAddress.getByName("::1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val buf = ByteArray(1024)
            val r = input.read(buf)
            if (r > 0) {
                output.write(buf, 0, r)
                output.flush()
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("fd7a:115c:a1e0::2"), 54335),
            dst = InetSocketAddress(InetAddress.getByName("::1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val msg = "Hello IPv6 TCP".toByteArray()
            sim.sendData(msg)

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < msg.size && System.currentTimeMillis() - start < 3000) {
                Thread.sleep(20)
            }
            assertArrayEquals(msg, sim.receivedData.toByteArray())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 60000)
    fun parallel_200_flows_each_20kb() {
        val server = ServerSocket(0, 300, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val pool = Executors.newCachedThreadPool()
        val serverRunning = AtomicBoolean(true)
        val serverThread = Thread {
            while (serverRunning.get()) {
                try {
                    val client = server.accept()
                    pool.submit {
                        try {
                            val input = client.getInputStream()
                            val output = client.getOutputStream()
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                output.flush()
                            }
                        } catch (_: Exception) {
                        } finally {
                            client.close()
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
        serverThread.start()

        val numFlows = 200
        val payloadSize = 20 * 1024
        val payload = ByteArray(payloadSize) { (it and 0xFF).toByte() }
        val successCount = AtomicLong(0)
        val clientPool = Executors.newFixedThreadPool(40)

        // Run in batches of 40 flows
        val batchSize = 40
        for (batchStart in 0 until numFlows step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, numFlows)
            val batchLatch = CountDownLatch(batchEnd - batchStart)
            for (i in batchStart until batchEnd) {
                clientPool.submit {
                    val sim = TcpClientSim(
                        tun = fakeTun,
                        src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 10000 + i),
                        dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
                    )
                    try {
                        if (sim.connect(timeoutSeconds = 5)) {
                            sim.sendData(payload)
                            val start = System.currentTimeMillis()
                            while (sim.receivedData.size() < payloadSize && System.currentTimeMillis() - start < 5000) {
                                Thread.sleep(20)
                            }
                            if (sim.receivedData.size() == payloadSize) {
                                successCount.incrementAndGet()
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        sim.stop()
                        batchLatch.countDown()
                    }
                }
            }
            batchLatch.await(8, TimeUnit.SECONDS)
        }

        serverRunning.set(false)
        server.close()
        serverThread.join(1000)
        pool.shutdownNow()
        clientPool.shutdownNow()

        assertEquals("All 200 parallel flows should succeed", numFlows.toLong(), successCount.get())
    }

    @Test(timeout = 15000)
    fun c1_end_of_stream_spin() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val size = 1024 * 1024 // 1 MiB
        val data = ByteArray(size) { (it and 0xFF).toByte() }

        val serverThread = Thread {
            val client = server.accept()
            val output = client.getOutputStream()
            output.write(data)
            output.flush()
            client.close() // Server closes -> EOF
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54336),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue("Handshake should complete", sim.connect())

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < size && System.currentTimeMillis() - start < 10000) {
                Thread.sleep(20)
            }
            assertEquals(size, sim.receivedData.size())

            assertTrue("Client should receive FIN", sim.finReceivedLatch.await(5, TimeUnit.SECONDS))
            sim.sendFin() // Client closes too

            // Count FIN packets in fakeTun directed to client
            val finPackets = fakeTun.fromForwarder.count { pkt ->
                val buf = ByteBuffer.wrap(pkt)
                val parsed = PacketParser().parse(buf, pkt.size)
                parsed is ParsedPacket.Tcp && (parsed.flags and TcpFlags.FIN) != 0 && parsed.dst == sim.src
            }
            assertEquals("Forwarder must send exactly one FIN toward the app", 1, finPackets)

            // Selector wakeups should stay under 200
            val wakeupsBefore = forwarder.selectorWakeups.get()
            assertTrue("Wakeups ($wakeupsBefore) should be under 200", wakeupsBefore < 200)

            // Wait 2 seconds and check CPU idle (wakeups do not grow by more than normal idle select timeouts ~20)
            Thread.sleep(2000)
            val wakeupsAfter = forwarder.selectorWakeups.get()
            val diff = wakeupsAfter - wakeupsBefore
            assertTrue("Idle CPU after close: wakeups grew by $diff, expected <= 30", diff <= 30)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun c1_client_closes_first_idle_cpu() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val buf = ByteArray(1024)
            while (input.read(buf) != -1) {}
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54337),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            sim.sendData("test".toByteArray())
            sim.sendFin()
            assertTrue("Client should receive FIN", sim.finReceivedLatch.await(5, TimeUnit.SECONDS))

            val wakeupsBefore = forwarder.selectorWakeups.get()
            Thread.sleep(2000)
            val diff = forwarder.selectorWakeups.get() - wakeupsBefore
            assertTrue("Idle CPU after client close: wakeups grew by $diff, expected <= 30", diff <= 30)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun c1_server_rst_idle_cpu() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            client.setSoLinger(true, 0)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54338),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            assertTrue("Client should receive RST", sim.rstReceivedLatch.await(5, TimeUnit.SECONDS))

            val wakeupsBefore = forwarder.selectorWakeups.get()
            Thread.sleep(2000)
            val diff = forwarder.selectorWakeups.get() - wakeupsBefore
            assertTrue("Idle CPU after server RST: wakeups grew by $diff, expected <= 30", diff <= 30)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun c1_simultaneous_close_idle_cpu() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val out = client.getOutputStream()
            out.write("hi".toByteArray())
            out.flush()
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54339),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            sim.sendFin()
            assertTrue("Client should receive FIN", sim.finReceivedLatch.await(5, TimeUnit.SECONDS))

            val wakeupsBefore = forwarder.selectorWakeups.get()
            Thread.sleep(2000)
            val diff = forwarder.selectorWakeups.get() - wakeupsBefore
            assertTrue("Idle CPU after simultaneous close: wakeups grew by $diff, expected <= 30", diff <= 30)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 60000)
    fun c2_fin_must_not_ack_unrelayed_data() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val size = 16 * 1024 * 1024 // 16 MiB
        val random = Random(42)
        val uploadBytes = ByteArray(size).also { random.nextBytes(it) }
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(uploadBytes)

        val serverDigestRef = AtomicReference<ByteArray>()
        val readBarrierLatch = CountDownLatch(1)

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(65536)

            // Read first 64 KiB
            var firstRead = 0
            while (firstRead < 65536) {
                val r = input.read(buf, firstRead, 65536 - firstRead)
                if (r <= 0) break
                firstRead += r
            }
            digest.update(buf, 0, firstRead)

            // Wait on latch barrier before reading the rest
            readBarrierLatch.await(10, TimeUnit.SECONDS)

            var totalRead = firstRead
            while (totalRead < size) {
                val r = input.read(buf, 0, minOf(buf.size, size - totalRead))
                if (r <= 0) break
                digest.update(buf, 0, r)
                totalRead += r
            }

            serverDigestRef.set(digest.digest())
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54340),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue("Handshake should complete", sim.connect())

            // Stream 16 MiB and send FIN immediately
            var sent = 0
            val chunkSize = 32768
            while (sent < size) {
                val chunkLen = minOf(chunkSize, size - sent)
                val chunk = ByteArray(chunkLen)
                System.arraycopy(uploadBytes, sent, chunk, 0, chunkLen)
                sim.sendData(chunk)
                sent += chunkLen
                if (sent % (1024 * 1024) == 0) {
                    Thread.sleep(5)
                }
            }
            sim.sendFin()

            // At this point, the server is paused at readBarrierLatch.
            // The client sent 16 MiB + FIN. The forwarder must NOT have ACKed the FIN yet!
            // (lastPeerAck must be less than sim.seq, which is initialSeq + 1 + 16 MiB + 1)
            val finSeq = sim.seq // seq was advanced after sendFin()
            assertTrue("FIN must not be acknowledged before all data written to server: lastPeerAck=${sim.lastPeerAck} < finSeq=$finSeq",
                sim.lastPeerAck < finSeq)

            // Release barrier so server reads all
            readBarrierLatch.countDown()

            val start = System.currentTimeMillis()
            while (sim.lastPeerAck < finSeq && System.currentTimeMillis() - start < 35000) {
                Thread.sleep(50)
            }
            assertEquals("FIN must eventually be acknowledged", finSeq, sim.lastPeerAck)

            serverThread.join(5000)
            assertArrayEquals("Server must receive all 16 MiB byte-identical (SHA-256)", expectedSha, serverDigestRef.get())
        } finally {
            readBarrierLatch.countDown()
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 10000)
    fun c2_out_of_order_fin_not_accepted_early() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverSawEof = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val buf = ByteArray(1024)
            while (input.read(buf) != -1) {}
            serverSawEof.countDown()
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54341),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())

            val chunk1 = "FIRST".toByteArray()
            val chunk2 = "SECOND".toByteArray()

            val seq1 = sim.seq
            val seq2 = (seq1 + chunk1.size) and 0xFFFFFFFFL
            val finSeq = (seq2 + chunk2.size) and 0xFFFFFFFFL

            // Send FIN with sequence number beyond rcvNext (gap of chunk1 + chunk2)
            sim.sendRawPacket(flags = TcpFlags.FIN or TcpFlags.ACK, seqNum = finSeq, ackNum = sim.ack)
            Thread.sleep(200)

            // Server must NOT see EOF
            assertEquals("Server must not see EOF when FIN has missing data before it", 1L, serverSawEof.count)
            // Forwarder must not have ACKed finSeq
            assertTrue("Forwarder must not ACK out of order FIN early", sim.lastPeerAck < (finSeq + 1))

            // Now send chunk1 then chunk2
            sim.sendRawPacket(flags = TcpFlags.ACK, seqNum = seq1, ackNum = sim.ack, payload = chunk1)
            Thread.sleep(100)
            sim.sendRawPacket(flags = TcpFlags.ACK, seqNum = seq2, ackNum = sim.ack, payload = chunk2)

            // Now that gap is filled, send FIN or forwarder processes buffered FIN
            sim.sendRawPacket(flags = TcpFlags.FIN or TcpFlags.ACK, seqNum = finSeq, ackNum = sim.ack)
            assertTrue("Server should see EOF once gap is filled and FIN processed", serverSawEof.await(5, TimeUnit.SECONDS))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun i1_app_window_in_flight_cap_and_pause() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverStartedWritingLatch = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            val output = client.getOutputStream()
            serverStartedWritingLatch.await(5, TimeUnit.SECONDS)
            val chunk = ByteArray(8192) { 0x5A }
            for (i in 0 until 10) {
                output.write(chunk)
                output.flush()
                Thread.sleep(50)
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54342),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort),
            advertisedWindow = 4096
        )

        try {
            assertTrue(sim.connect())

            // Disable Sim's automatic ACKing before server writes, so in-flight bytes build up without being ACKed
            sim.autoAck = false
            serverStartedWritingLatch.countDown()

            // Let server write chunks
            Thread.sleep(300)

            // Check total distinct payload bytes written by forwarder without client ACKs
            val packets = fakeTun.fromForwarder.mapNotNull { pkt ->
                val buf = ByteBuffer.wrap(pkt)
                val parsed = PacketParser().parse(buf, pkt.size)
                if (parsed is ParsedPacket.Tcp && parsed.src == sim.dst && parsed.dst == sim.src && parsed.payloadLength > 0) {
                    parsed
                } else null
            }
            val minSeq = packets.minOfOrNull { it.seq } ?: 0L
            val maxSeqEnd = packets.maxOfOrNull { it.seq + it.payloadLength } ?: 0L
            val unackedBytes = (maxSeqEnd - minSeq).toInt()

            assertTrue("Forwarder sent $unackedBytes unacked bytes, expected <= 4096", unackedBytes <= 4096)

            // Now advertise window 0
            sim.updateWindow(0)
            Thread.sleep(200)
            val packetsAtWindow0 = fakeTun.fromForwarder.size

            Thread.sleep(500)
            // While window is 0, no new regular data segments (> 1 byte beyond maxSeqEnd) should be emitted
            val newRegularDataPackets = fakeTun.fromForwarder.toList().drop(packetsAtWindow0).count { pkt ->
                val buf = ByteBuffer.wrap(pkt)
                val parsed = PacketParser().parse(buf, pkt.size)
                parsed is ParsedPacket.Tcp && parsed.src == sim.dst && parsed.dst == sim.src && parsed.payloadLength > 1 && parsed.seq >= maxSeqEnd
            }
            assertEquals("No new regular data packets written while window is 0", 0, newRegularDataPackets)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun i1_zero_window_probe_keeps_alive() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val windowZeroLatch = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            val output = client.getOutputStream()
            windowZeroLatch.await(5, TimeUnit.SECONDS)
            output.write("probedata".toByteArray())
            output.flush()
            Thread.sleep(3000)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54343),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort),
            advertisedWindow = 4096
        )

        try {
            assertTrue(sim.connect())

            // Now client advertises window 0
            sim.updateWindow(0)
            Thread.sleep(100)

            // Disable sim automatic responses so window stays 0 and client doesn't ACK probes
            sim.autoAck = false

            // Signal server to write now that window is 0
            windowZeroLatch.countDown()

            // Advance time and check if zero-window probe packets are emitted
            val packetsStart = fakeTun.fromForwarder.size
            Thread.sleep(2000)

            val probePackets = fakeTun.fromForwarder.toList().drop(packetsStart).filter { pkt ->
                val buf = ByteBuffer.wrap(pkt)
                val parsed = PacketParser().parse(buf, pkt.size)
                parsed is ParsedPacket.Tcp && parsed.src == sim.dst && parsed.dst == sim.src && parsed.payloadLength == 1
            }
            assertTrue("Expected at least one 1-byte zero-window probe sent, found ${probePackets.size}", probePackets.isNotEmpty())

            // And flow must NOT receive RST
            assertFalse("Flow must NOT receive premature RST during zero-window probing",
                sim.rstReceivedLatch.await(100, TimeUnit.MILLISECONDS))
        } finally {
            windowZeroLatch.countDown()
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 60000)
    fun i2_advertised_window_and_upload_slow_server() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val size = 16 * 1024 * 1024 // 16 MiB
        val random = Random(99)
        val uploadBytes = ByteArray(size).also { random.nextBytes(it) }

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val buf = ByteArray(32768)
            var total = 0
            while (total < size) {
                val r = input.read(buf)
                if (r <= 0) break
                total += r
                Thread.sleep(1) // Slow reader
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54344),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())

            var sent = 0
            val chunkSize = 32768
            var retransmissionsSeen = 0
            val retransmittedSeqs = HashSet<Long>()

            while (sent < size) {
                val chunkLen = minOf(chunkSize, size - sent)
                val chunk = ByteArray(chunkLen)
                System.arraycopy(uploadBytes, sent, chunk, 0, chunkLen)
                sim.sendData(chunk)
                sent += chunkLen
                if (sent % (512 * 1024) == 0) {
                    Thread.sleep(10)
                }
            }
            sim.sendFin()

            val start = System.currentTimeMillis()
            while (sim.lastPeerAck < sim.seq && System.currentTimeMillis() - start < 45000) {
                Thread.sleep(100)
            }

            // Check advertised window in packets received from forwarder
            val forwarderPackets = fakeTun.fromForwarder.mapNotNull { pkt ->
                val buf = ByteBuffer.wrap(pkt)
                val parsed = PacketParser().parse(buf, pkt.size)
                if (parsed is ParsedPacket.Tcp && parsed.src == sim.dst && parsed.dst == sim.src) {
                    parsed
                } else null
            }
            val advertisedWindows = forwarderPackets.map { it.window }
            // In unfixed code, advertised window is hardcoded to 65535 on all packets
            val hasVaryingWindow = advertisedWindows.any { it < 65535 }
            assertTrue("Advertised window should reflect upload buffer free space and be < 65535 when buffer fills", hasVaryingWindow)

            // Assert a pure-ACK window update was sent (payloadLength == 0 with reopened window after throttling)
            val pureAckWindowUpdates = forwarderPackets.filter { it.payloadLength == 0 && (it.flags and TcpFlags.ACK) != 0 && it.window > 0 }
            assertTrue("Pure-ACK window update must be sent when upload buffer space frees up", pureAckWindowUpdates.isNotEmpty())

            // Assert retransmissions seen by client stayed under 1% of total segments
            val totalClientSegments = fakeTun.toForwarder.mapNotNull { pkt ->
                val buf = ByteBuffer.wrap(pkt)
                val parsed = PacketParser().parse(buf, pkt.size)
                if (parsed is ParsedPacket.Tcp && parsed.src == sim.src && parsed.dst == sim.dst && parsed.payloadLength > 0) {
                    parsed
                } else null
            }
            val uniqueSeqs = totalClientSegments.map { it.seq }.toSet()
            val clientRetransmissions = totalClientSegments.size - uniqueSeqs.size
            val retransmissionRate = if (totalClientSegments.isNotEmpty()) clientRetransmissions.toDouble() / totalClientSegments.size else 0.0
            assertTrue("Retransmissions seen by client ($clientRetransmissions / ${totalClientSegments.size} = $retransmissionRate) must stay under 1%", retransmissionRate < 0.01)

            assertEquals("Dropped segments must be 0", 0L, forwarder.droppedSegments.get())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun tcp_sequence_wraparound_upload_and_download_at_0xFFFFFFF0() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val payloadSize = 2000
        val uploadBytes = ByteArray(payloadSize) { (it and 0xFF).toByte() }
        val serverReceived = java.io.ByteArrayOutputStream()

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val buf = ByteArray(1024)
            var total = 0
            while (total < payloadSize) {
                val r = input.read(buf)
                if (r <= 0) break
                serverReceived.write(buf, 0, r)
                output.write(buf, 0, r) // echo back for download wraparound check
                output.flush()
                total += r
            }
            client.close()
            server.close()
        }
        serverThread.start()

        // Start client sequence number at 0xFFFFFFF0
        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54345),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort),
            seq = 0xFFFFFFF0L
        )

        try {
            assertTrue("Handshake at 0xFFFFFFF0 should succeed", sim.connect())

            sim.sendData(uploadBytes)

            val start = System.currentTimeMillis()
            while ((serverReceived.size() < payloadSize || sim.receivedData.size() < payloadSize) && System.currentTimeMillis() - start < 8000) {
                Thread.sleep(20)
            }

            assertEquals("Server must receive all bytes across upload wraparound", payloadSize, serverReceived.size())
            assertArrayEquals("Server received payload matches", uploadBytes, serverReceived.toByteArray())
            assertEquals("Client must receive all bytes across download wraparound", payloadSize, sim.receivedData.size())
            assertArrayEquals("Client received payload matches", uploadBytes, sim.receivedData.toByteArray())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 25000)
    fun rst_after_5_retransmissions_and_rto_doubling() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val clientConnected = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            clientConnected.await(5, TimeUnit.SECONDS)
            val output = client.getOutputStream()
            output.write("TriggerData".toByteArray())
            output.flush()
            Thread.sleep(15000)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54346),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())

            val key = FlowKey(6, sim.src, sim.dst)
            val internalField = Forwarder::class.java.getDeclaredField("flowTable")
            internalField.isAccessible = true
            val table = internalField.get(forwarder) as FlowTable
            val flow = table.getTcp(key)
            assertNotNull("TcpFlow must exist", flow)

            // Disable sim automatic ACKing so downstream packets from forwarder remain unacked
            sim.autoAck = false

            // Now release server to write data
            clientConnected.countDown()

            // Wait for in-flight segment to appear in TcpFlow
            val inFlightField = TcpFlow::class.java.getDeclaredField("inFlightSegments")
            inFlightField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val inFlight = inFlightField.get(flow) as java.util.ArrayDeque<Any>

            val startPkt = System.currentTimeMillis()
            while (inFlight.isEmpty() && System.currentTimeMillis() - startPkt < 4000) {
                Thread.sleep(20)
            }
            assertTrue("There must be an in-flight segment", inFlight.isNotEmpty())

            val seg = inFlight.first
            val rtoField = seg.javaClass.getDeclaredField("rtoMs")
            rtoField.isAccessible = true
            val sendTimeField = seg.javaClass.getDeclaredField("sendTimeMs")
            sendTimeField.isAccessible = true
            val attemptsField = seg.javaClass.getDeclaredField("attempts")
            attemptsField.isAccessible = true

            // Set initial RTO to 1000ms
            rtoField.setLong(seg, 1000L)
            sendTimeField.setLong(seg, System.currentTimeMillis())
            attemptsField.setInt(seg, 1)

            // Verify RTO doubling: 1s, 2s, 4s, 8s, 16s across attempts
            val expectedRtos = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
            for (i in 0 until 4) {
                val currentRto = rtoField.getLong(seg)
                assertEquals("Attempt ${i + 1} RTO must match expected doubling", expectedRtos[i], currentRto)

                // Advance clock / sendTimeMs artificially to trigger next retransmission
                sendTimeField.setLong(seg, System.currentTimeMillis() - currentRto - 10L)
                flow!!.checkRetransmissions()

                val nextRto = rtoField.getLong(seg)
                assertEquals("Next RTO after retransmission must double", expectedRtos[i + 1], nextRto)
                assertEquals("Attempts count incremented", i + 2, attemptsField.getInt(seg))
                assertFalse("Flow must NOT send RST prematurely", sim.rstReceivedLatch.await(50, TimeUnit.MILLISECONDS))
            }

            // At 10th timeout, checkRetransmissions triggers RST (when attempts reaches >= 10)
            while (attemptsField.getInt(seg) < 10) {
                sendTimeField.setLong(seg, System.currentTimeMillis() - rtoField.getLong(seg) - 10L)
                flow!!.checkRetransmissions()
            }
            // Trigger 10th timeout:
            sendTimeField.setLong(seg, System.currentTimeMillis() - rtoField.getLong(seg) - 10L)
            flow!!.checkRetransmissions()

            assertTrue("Client must see RST after retransmissions exceeded", sim.rstReceivedLatch.await(3, TimeUnit.SECONDS))
            assertTrue("Flow state must be CLOSED", flow.state == TcpFlow.State.CLOSED)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 25000)
    fun upload_buffer_256k_limit_and_advertised_window_zero() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverAccepted = CountDownLatch(1)
        val serverThread = Thread {
            val client = server.accept()
            // Set socket receive buffer to minimum possible so OS buffers quickly fill
            try { client.receiveBufferSize = 4096 } catch (_: Exception) {}
            serverAccepted.countDown()
            // Server NEVER reads from client socket!
            Thread.sleep(15000)
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54347),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            serverAccepted.await(3, TimeUnit.SECONDS)

            val key = FlowKey(6, sim.src, sim.dst)
            val internalField = Forwarder::class.java.getDeclaredField("flowTable")
            internalField.isAccessible = true
            val table = internalField.get(forwarder) as FlowTable
            val flow = table.getTcp(key)
            assertNotNull("TcpFlow must exist", flow)

            val upstreamBytesField = TcpFlow::class.java.getDeclaredField("upstreamBufferedBytes")
            upstreamBytesField.isAccessible = true

            // Send 300 KiB of data without server reading
            val chunk = ByteArray(32768) { 0x41.toByte() }
            for (i in 0 until 12) {
                sim.sendData(chunk)
                Thread.sleep(30)
            }

            // Wait a moment for networkThread to process packets
            val startWait = System.currentTimeMillis()
            while (upstreamBytesField.getInt(flow) == 0 && System.currentTimeMillis() - startWait < 3000) {
                Thread.sleep(50)
            }

            val buffered = upstreamBytesField.getInt(flow)
            assertTrue("Upload buffer must buffer unread data, was: $buffered", buffered > 0)

            // Calculate rcvWnd via calculateRcvWnd()
            val calcRcvWndMethod = TcpFlow::class.java.getDeclaredMethod("calculateRcvWnd")
            calcRcvWndMethod.isAccessible = true
            val calculatedWindow = calcRcvWndMethod.invoke(flow) as Int
            assertTrue("Calculated advertised window should drop below 65535 when buffer fills: was $calculatedWindow", calculatedWindow < 65535)
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 15000)
    fun time_wait_expiry_30s_after_both_fins() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val buf = ByteArray(10)
            client.getInputStream().read(buf)
            client.close() // Server closes -> emits FIN
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54348),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val flowId = testListener.openedFlows.keys.firstOrNull()
            assertNotNull(flowId)

            val key = FlowKey(6, sim.src, sim.dst)
            val internalField = Forwarder::class.java.getDeclaredField("flowTable")
            internalField.isAccessible = true
            val table = internalField.get(forwarder) as FlowTable
            val flow = table.getTcp(key)
            assertNotNull("TcpFlow must exist", flow)

            sim.sendData("hi".toByteArray())
            assertTrue("Client should receive server FIN", sim.finReceivedLatch.await(5, TimeUnit.SECONDS))
            sim.sendFin() // Client sends FIN too -> enters TIME_WAIT
            Thread.sleep(200)

            assertEquals("Flow state must be TIME_WAIT", TcpFlow.State.TIME_WAIT, flow!!.state)
            assertNotNull("closedAt must be set upon entering TIME_WAIT", flow.closedAt)

            // At 29 seconds: flow must still be open / alive
            clock.advance(Duration.ofSeconds(29))
            Thread.sleep(100)
            assertFalse("Flow must NOT be dead at 29s", flow.isDead(clock.instant()))
            assertFalse("Flow must NOT be closed at 29s", testListener.closedFlows.containsKey(flowId))

            // At 31 seconds: flow is dead and cleaned up
            clock.advance(Duration.ofSeconds(2))
            Thread.sleep(200)
            assertTrue("Flow must be dead at 31s", flow.isDead(clock.instant()))

            val start = System.currentTimeMillis()
            while (!testListener.closedFlows.containsKey(flowId) && System.currentTimeMillis() - start < 3000) {
                clock.advance(Duration.ofSeconds(1))
                Thread.sleep(50)
            }
            assertTrue("Flow must be closed after 30s in TIME_WAIT", testListener.closedFlows.containsKey(flowId))
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }
}


