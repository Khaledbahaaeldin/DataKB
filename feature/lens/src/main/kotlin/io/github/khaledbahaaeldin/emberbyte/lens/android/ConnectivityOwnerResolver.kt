package io.github.khaledbahaaeldin.emberbyte.lens.android

import android.net.ConnectivityManager
import android.os.Process
import io.github.khaledbahaaeldin.emberbyte.lens.core.OwnerResolver
import java.net.InetSocketAddress

class ConnectivityOwnerResolver(
    private val cm: ConnectivityManager
) : OwnerResolver {

    override fun uidOf(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int {
        return try {
            val uid = cm.getConnectionOwnerUid(protocol, local, remote)
            if (uid == Process.INVALID_UID) -1 else uid
        } catch (_: Throwable) {
            -1
        }
    }
}
