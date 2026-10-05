package io.github.khaledbahaaeldin.emberbyte.data.fake

import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfo
import io.github.khaledbahaaeldin.emberbyte.data.source.SimInfoSource

class FakeSimInfoSource(
    var sims: List<SimInfo> = emptyList(),
    var defaultId: Int = -1,
) : SimInfoSource {
    override fun activeSims(): List<SimInfo> = sims
    override fun defaultDataSubscriptionId(): Int = defaultId
}
