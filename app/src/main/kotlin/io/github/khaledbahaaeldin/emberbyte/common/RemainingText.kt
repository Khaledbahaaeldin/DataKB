package io.github.khaledbahaaeldin.emberbyte.common

import io.github.khaledbahaaeldin.emberbyte.ui.design.format.ByteUnits

internal fun remainingText(bytes: Long, units: ByteUnits): String =
    if (bytes <= 0L) "No data left" else "${plainBytes(bytes, units)} left"
