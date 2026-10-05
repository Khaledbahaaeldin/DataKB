package io.github.khaledbahaaeldin.emberbyte.data.plan

import io.github.khaledbahaaeldin.emberbyte.engine.model.AddOn
import io.github.khaledbahaaeldin.emberbyte.engine.model.Cycle
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError.Invalid
import io.github.khaledbahaaeldin.emberbyte.engine.model.FreeRule
import io.github.khaledbahaaeldin.emberbyte.engine.model.Plan
import io.github.khaledbahaaeldin.emberbyte.engine.model.Rollover

/** Contract 10.2 validation: the first problem found, naming the field. null = valid. */
internal object PlanValidation {
    private const val MAX_CAP = 1_000_000_000_000_000L

    fun planAll(plan: Plan): Map<String, String> {
        val problems = LinkedHashMap<String, String>()
        if (plan.name.isBlank() || plan.name.trim().length > 40) problems["name"] = "Enter a name of up to 40 characters"
        if (plan.capBytes !in 1L..MAX_CAP) problems["capBytes"] = "Enter a cap greater than zero"
        when (val cycle = plan.cycle) {
            is Cycle.MonthlyOnDay -> if (cycle.dayOfMonth !in 1..31) problems["cycle.dayOfMonth"] = "Choose a day from 1 to 31"
            is Cycle.EveryNDays -> if (cycle.days !in 1..366) problems["cycle.days"] = "Choose from 1 to 366 days"
        }
        val rollover = plan.rollover
        if (rollover is Rollover.Capped && rollover.maxBytes <= 0L) problems["rollover.maxBytes"] = "Enter an amount greater than zero"
        val subscription = plan.subscriptionId
        if (subscription != null && subscription < 0) problems["subscriptionId"] = "Choose a SIM from the list"
        return problems
    }

    fun plan(plan: Plan): Invalid? =
        planAll(plan).entries.firstOrNull()?.let { Invalid(it.key, it.value) }

    fun addOn(addOn: AddOn): Invalid? = when {
        addOn.label.isBlank() -> Invalid("label", "Enter a label")
        addOn.bytes <= 0L -> Invalid("bytes", "Enter an amount greater than zero")
        addOn.validUntil <= addOn.validFrom -> Invalid("validUntil", "Must be after the start")
        else -> null
    }

    fun freeRule(rule: FreeRule): Invalid? = when {
        rule.label.isBlank() -> Invalid("label", "Enter a label")
        rule.days.isEmpty() -> Invalid("days", "Choose at least one day")
        rule.start == rule.end -> Invalid("time", "Start and end must differ")
        else -> null
    }
}
