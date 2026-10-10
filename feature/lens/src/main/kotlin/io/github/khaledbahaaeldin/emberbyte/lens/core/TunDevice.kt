package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.nio.ByteBuffer

interface TunDevice {
    fun read(buffer: ByteBuffer): Int
    fun write(buffer: ByteBuffer, length: Int)
    fun close()
}
