package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.nio.ByteBuffer
import java.util.ArrayDeque

class DirectBufferPool(
    val bufferCapacity: Int = 2048,
    val maxPoolSize: Int = 1024
) {
    private val pool = ArrayDeque<ByteBuffer>()

    @Synchronized
    fun acquire(): ByteBuffer {
        return if (pool.isNotEmpty()) {
            val buf = pool.removeLast()
            buf.clear()
            buf
        } else {
            ByteBuffer.allocateDirect(bufferCapacity)
        }
    }

    @Synchronized
    fun release(buf: ByteBuffer) {
        if (pool.size < maxPoolSize) {
            buf.clear()
            pool.addLast(buf)
        }
    }
}
