package io.github.khaledbahaaeldin.emberbyte.lens.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.SelectableChannel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ForwarderLimitsTest {

    private lateinit var fakeTun: FakeTun
    private lateinit var forwarder: Forwarder
    private lateinit var forwarderThread: Thread
    private lateinit var testListener: TestFlowListener
    private var clock = TestClock()
    private val countingProtector = CountingProtector()

    class CountingProtector : SocketProtector {
        val openSockets = AtomicInteger(0)
        override fun protect(socket: Socket): Boolean {
            openSockets.incrementAndGet()
            return true
        }
        override fun protect(socket: DatagramSocket): Boolean {
            openSockets.incrementAndGet()
            return true
        }
        override fun protect(channel: SelectableChannel): Boolean {
            openSockets.incrementAndGet()
            return true
        }
    }

    private val ownerResolver = object : OwnerResolver {
        override fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int = 10042
    }

    class TestFlowListener : FlowListener {
        val openedFlows = ConcurrentHashMap<Long, FlowInfo>()
        val closedFlows = ConcurrentHashMap<Long, Boolean>()
        override fun onFlowOpened(flow: FlowInfo) { openedFlows[flow.flowId] = flow }
        override fun onBytes(flowId: Long, rxBytes: Long, txBytes: Long) {}
        override fun onName(flowId: Long, name: String) {}
        override fun onDns(answers: List<DnsAnswer>) {}
        override fun onFlowClosed(flowId: Long) { closedFlows[flowId] = true }
    }

    class TestClock(var currentInstant: Instant = Instant.parse("2026-10-11T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = currentInstant
        fun advance(duration: Duration) { currentInstant = currentInstant.plus(duration) }
    }

    @Before
    fun setUp() {
        clock = TestClock()
        fakeTun = FakeTun()
        testListener = TestFlowListener()
        forwarder = Forwarder(
            tun = fakeTun,
            protector = countingProtector,
            owner = ownerResolver,
            listener = testListener,
            clock = clock,
            maxFlows = 8, // Limit set to 8
            mtu = 1400
        )
        forwarderThread = Thread({ forwarder.run() }, "Forwarder-Limits-Thread")
        forwarderThread.start()
    }

    @After
    fun tearDown() {
        forwarder.stop()
        fakeTun.close()
        forwarderThread.join(2000)
    }

    @Test(timeout = 10000)
    fun flow_limit_8_evicts_idle_longest_on_9th_flow() {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        try {
            val serverPort = server.localPort
            val serverDst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
            for (i in 1..8) {
                val src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 50000 + i)
                fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(src, serverDst, "hello".toByteArray()))
                clock.advance(Duration.ofSeconds(1))
                Thread.sleep(20)
            }

            // Wait for 8 flows to open
            val start = System.currentTimeMillis()
            while (testListener.openedFlows.size < 8 && System.currentTimeMillis() - start < 3000) {
                Thread.sleep(20)
            }
            assertEquals(8, testListener.openedFlows.size)
            assertEquals(0L, forwarder.evictedFlows.get())

            // The first flow (port 50001) has been idle the longest!
            val firstFlowId = testListener.openedFlows.entries.first { it.value.local.port == 50001 }.key

            // Now open 9th flow
            val src9 = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 50009)
            fakeTun.toForwarder.offer(PacketBuilder.ipv4Udp(src9, serverDst, "hello9".toByteArray()))

            val startEvict = System.currentTimeMillis()
            while (forwarder.evictedFlows.get() < 1L && System.currentTimeMillis() - startEvict < 3000) {
                Thread.sleep(20)
            }

            assertEquals(1L, forwarder.evictedFlows.get())
            assertTrue("Idle-longest flow should be closed", testListener.closedFlows.containsKey(firstFlowId))
        } finally {
            server.close()
        }
    }

    @Test(timeout = 15000)
    fun garbage_packets_interleaved_with_normal_transfer() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            val client = server.accept()
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val buf = ByteArray(1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                output.write(buf, 0, read)
                output.flush()
            }
            client.close()
            server.close()
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54399),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())

            val random = Random(4242)
            val normalMessage = "NormalMessage".toByteArray()

            // Feed 20,000 random garbage packets interleaved
            val garbageThread = Thread {
                for (i in 0 until 20000) {
                    val garbageLen = random.nextInt(100) + 1
                    val garbage = ByteArray(garbageLen)
                    random.nextBytes(garbage)
                    fakeTun.toForwarder.offer(garbage)
                    if (i % 5000 == 0) Thread.sleep(5)
                }
            }
            garbageThread.start()

            // Concurrently send normal message through sim
            sim.sendData(normalMessage)

            val start = System.currentTimeMillis()
            while (sim.receivedData.size() < normalMessage.size && System.currentTimeMillis() - start < 8000) {
                Thread.sleep(50)
            }

            garbageThread.join(5000)
            assertEquals("Normal transfer should complete despite 20,000 garbage packets", normalMessage.size, sim.receivedData.size())
            assertEquals("Normal message matches", "NormalMessage", sim.receivedData.toString())
        } finally {
            sim.stop()
            serverThread.join(1000)
        }
    }

    @Test(timeout = 5000)
    fun stop_returns_within_2s_and_releases_channels() {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverPort = server.localPort

        val serverThread = Thread {
            try {
                val client = server.accept()
                // Sleep to keep flow open
                Thread.sleep(5000)
                client.close()
            } catch (_: Exception) {} finally {
                server.close()
            }
        }
        serverThread.start()

        val sim = TcpClientSim(
            tun = fakeTun,
            src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54398),
            dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
        )

        try {
            assertTrue(sim.connect())
            val startStop = System.currentTimeMillis()
            forwarder.stop()
            forwarderThread.join(2000)
            val stopDuration = System.currentTimeMillis() - startStop
            assertTrue("stop() should complete in under 2 seconds: actual ${stopDuration}ms", stopDuration <= 2000)
            assertTrue("Forwarder thread should terminate", !forwarderThread.isAlive)
        } finally {
            sim.stop()
            serverThread.interrupt()
            serverThread.join(500)
        }
    }

    @Test(timeout = 8000)
    fun stop_releases_all_sockets_including_flows_blocked_in_connect() {
        val isolatedTun = FakeTun()
        val createdChannels = java.util.concurrent.CopyOnWriteArrayList<java.nio.channels.SocketChannel>()
        val customFactory = SocketChannelFactory {
            val sc = java.nio.channels.SocketChannel.open()
            createdChannels.add(sc)
            sc
        }

        val testForwarder = Forwarder(
            tun = isolatedTun,
            protector = countingProtector,
            owner = ownerResolver,
            listener = testListener,
            clock = clock,
            maxFlows = 16,
            mtu = 1400,
            channelFactory = customFactory
        )
        val th = Thread({ testForwarder.run() }, "Forwarder-Stop-Channels-Thread")
        th.start()

        try {
            // 1. Established flow
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val serverPort = server.localPort
            val serverThread = Thread {
                try {
                    val client = server.accept()
                    Thread.sleep(3000)
                    client.close()
                } catch (_: Exception) {} finally {
                    server.close()
                }
            }
            serverThread.start()

            val sim1 = TcpClientSim(
                tun = isolatedTun,
                src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54301),
                dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), serverPort)
            )
            assertTrue("Flow 1 should connect", sim1.connect())

            // 2. Flow blocked in connect: non-routable IP (192.0.2.1 TEST-NET-1 or 10.255.255.1)
            val nonRoutableDst = InetSocketAddress(InetAddress.getByName("192.0.2.1"), 80)
            val synBlocked = PacketBuilder.ipv4Tcp(
                src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 54302),
                dst = nonRoutableDst,
                seq = 1000L,
                ack = 0L,
                flags = TcpFlags.SYN
            )
            isolatedTun.toForwarder.offer(synBlocked)
            val startWait = System.currentTimeMillis()
            while (createdChannels.size < 2 && System.currentTimeMillis() - startWait < 3000) {
                Thread.sleep(20)
            }

            assertTrue("At least 2 channels created", createdChannels.size >= 2)

            val startStop = System.currentTimeMillis()
            testForwarder.stop()
            th.join(2000)
            val stopDuration = System.currentTimeMillis() - startStop
            assertTrue("stop() completed within 2 seconds: actual ${stopDuration}ms", stopDuration <= 2000)
            assertFalse("Forwarder thread must terminate", th.isAlive)

            // Verify EVERY created channel is closed
            for (ch in createdChannels) {
                assertFalse("All socket channels must be closed after stop() returns", ch.isOpen)
            }

            sim1.stop()
            serverThread.interrupt()
            serverThread.join(500)
        } finally {
            testForwarder.stop()
            isolatedTun.close()
            th.join(500)
        }
    }

    @Test
    fun owner_cache_bounded_at_4096_entries() {
        val cache = BoundedLruCache<FlowKey, Int>(4096)
        val dst = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 80)
        for (i in 1..10000) {
            val src = InetSocketAddress(InetAddress.getByName("10.7.0.2"), 1000 + (i % 60000))
            val key = FlowKey(6, src, dst)
            cache.computeIfAbsent(key) { 10000 + i }
        }
        assertEquals("Owner cache must be bounded at exactly 4096 entries", 4096, cache.size())
    }
}
