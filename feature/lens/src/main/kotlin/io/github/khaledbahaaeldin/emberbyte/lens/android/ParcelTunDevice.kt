package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.os.ParcelFileDescriptor
import io.github.khaledbahaaeldin.emberbyte.lens.core.TunDevice
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ParcelTunDevice(
    private val pfd: ParcelFileDescriptor
) : TunDevice {

    private val inStream = FileInputStream(pfd.fileDescriptor)
    private val outStream = FileOutputStream(pfd.fileDescriptor)
    private val isClosed = AtomicBoolean(false)

    val rxBytes = AtomicLong(0)
    val txBytes = AtomicLong(0)

    private val readBufferArray = ByteArray(65536)
    private val writeBufferArray = ByteArray(65536)
    private val writeLock = Any()

    override fun read(buffer: ByteBuffer): Int {
        if (isClosed.get()) return -1
        return try {
            val toRead = minOf(buffer.remaining(), readBufferArray.size)
            val bytesRead = inStream.read(readBufferArray, 0, toRead)
            if (bytesRead > 0) {
                buffer.put(readBufferArray, 0, bytesRead)
                rxBytes.addAndGet(bytesRead.toLong())
            }
            bytesRead
        } catch (e: IOException) {
            if (isClosed.get()) -1 else throw e
        }
    }

    override fun write(buffer: ByteBuffer, length: Int) {
        if (isClosed.get()) return
        synchronized(writeLock) {
            val toWrite = minOf(length, writeBufferArray.size)
            buffer.get(writeBufferArray, 0, toWrite)
            outStream.write(writeBufferArray, 0, toWrite)
            txBytes.addAndGet(toWrite.toLong())
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try { inStream.close() } catch (_: Throwable) {}
            try { outStream.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }
    }
}
