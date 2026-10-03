package io.github.khaledbahaaeldin.emberbyte.data.store

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryUsageStore

class InMemoryUsageStoreTest : UsageStoreContractTest() {
    override fun createStore(): UsageStore = InMemoryUsageStore()
}
