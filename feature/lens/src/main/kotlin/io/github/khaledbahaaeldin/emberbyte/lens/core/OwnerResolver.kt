package io.github.khaledbahaaeldin.emberbyte.lens.core

import java.net.InetSocketAddress

interface OwnerResolver {
    fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int
}
