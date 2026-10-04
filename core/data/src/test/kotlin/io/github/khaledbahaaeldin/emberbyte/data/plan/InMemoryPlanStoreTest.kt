package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryPlanStore

class InMemoryPlanStoreTest : PlanStoreContractTest() {
    override fun createStore(): PlanStore = InMemoryPlanStore()
}
