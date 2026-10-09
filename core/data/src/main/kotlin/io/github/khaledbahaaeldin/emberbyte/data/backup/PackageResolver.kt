package io.github.khaledbahaaeldin.emberbyte.data.backup

import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta

interface PackageResolver {
    fun resolve(packageName: String): AppMeta?
}
