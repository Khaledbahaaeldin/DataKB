package io.github.khaledbahaaeldin.emberbyte.lens.android

sealed interface LensStartResult {
    data object Success : LensStartResult
    data object NoConsent : LensStartResult

    companion object {
        @Volatile
        private var current: LensStartResult? = null

        fun get(): LensStartResult? = current

        fun set(result: LensStartResult?) {
            current = result
        }
    }
}
