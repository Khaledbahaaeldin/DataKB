package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.util.ArrayDeque

class ByteArrayPool(
    val arrayCapacity: Int = 2048,
    val maxPoolSize: Int = 1024
) {
    private val pool = ArrayDeque<ByteArray>()

    @Synchronized
    fun acquire(): ByteArray {
        return if (pool.isNotEmpty()) {
            pool.removeLast()
        } else {
            ByteArray(arrayCapacity)
        }
    }

    @Synchronized
    fun release(arr: ByteArray) {
        if (arr.size == arrayCapacity && pool.size < maxPoolSize) {
            pool.addLast(arr)
        }
    }
}
