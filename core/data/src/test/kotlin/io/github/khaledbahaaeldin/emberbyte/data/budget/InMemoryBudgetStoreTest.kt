package io.github.khaledbahaaeldin.emberbyte.data.budget

import io.github.khaledbahaaeldin.emberbyte.data.fake.InMemoryBudgetStore

class InMemoryBudgetStoreTest : BudgetStoreContractTest() {
    override fun createStore(): BudgetStore = InMemoryBudgetStore()
}
