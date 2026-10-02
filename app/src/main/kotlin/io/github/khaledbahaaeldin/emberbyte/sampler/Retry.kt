package io.github.khaledbahaaeldin.emberbyte.sampler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs [block]; when it throws (other than cancellation) it reports the error, waits and runs it again, doubling the wait up to
 * [maxMillis]. Returns when [block] returns normally. Cancellation always propagates.
 */
suspend fun retryWithBackoff(
    initialMillis: Long,
    maxMillis: Long,
    onError: (Throwable) -> Unit,
    block: suspend () -> Unit,
) {
    var wait = initialMillis
    while (true) {
        try {
            block()
            return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error)
            delay(wait)
            wait = minOf(wait * 2, maxMillis)
        }
    }
}
