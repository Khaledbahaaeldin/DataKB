package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class FakeTun : TunDevice {
    val toForwarder = LinkedBlockingQueue<ByteArray>()
    val fromForwarder = LinkedBlockingQueue<ByteArray>()
    private val packetSubscribers = java.util.concurrent.CopyOnWriteArrayList<(ByteArray) -> Unit>()
    private val closed = AtomicBoolean(false)

    fun subscribe(subscriber: (ByteArray) -> Unit) {
        packetSubscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: (ByteArray) -> Unit) {
        packetSubscribers.remove(subscriber)
    }

    override fun read(buffer: ByteBuffer): Int {
        while (!closed.get()) {
            val pkt = toForwarder.poll(100, TimeUnit.MILLISECONDS)
            if (pkt != null) {
                val len = minOf(buffer.remaining(), pkt.size)
                buffer.put(pkt, 0, len)
                return len
            }
        }
        return -1
    }

    override fun write(buffer: ByteBuffer, length: Int) {
        if (closed.get()) return
        val bytes = ByteArray(length)
        buffer.get(bytes)
        fromForwarder.offer(bytes)
        for (sub in packetSubscribers) {
            try {
                sub.invoke(bytes)
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        closed.set(true)
    }

    fun isClosed(): Boolean = closed.get()
}
