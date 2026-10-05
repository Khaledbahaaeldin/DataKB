package io.github.khaledbahaaeldin.emberbyte.data.spike

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemorySpikeStore

class InMemorySpikeStoreTest : SpikeStoreContractTest() {
    override fun createStore(): SpikeStore = InMemorySpikeStore()
}
