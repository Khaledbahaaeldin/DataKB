package io.github.khaledbahaaeldin.emberbyte.engine.model

const val CONTRACT_VERSION = 1

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: EmberbyteError) : Outcome<Nothing>
}

sealed interface EmberbyteError {
    data object MissingUsageAccess : EmberbyteError
    data object MissingPhoneState : EmberbyteError
    data object NotFound : EmberbyteError
    data class Invalid(val field: String, val reason: String) : EmberbyteError
    data class Storage(val cause: String) : EmberbyteError
    data class Unexpected(val cause: String) : EmberbyteError
}
