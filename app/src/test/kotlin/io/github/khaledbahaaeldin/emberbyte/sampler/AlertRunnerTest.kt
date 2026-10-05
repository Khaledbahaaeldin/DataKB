package io.github.khaledbahaaeldin.emberbyte.sampler

import io.github.khaledbahaaeldin.emberbyte.data.alert.AlertNotice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertRunnerTest {
    private val notice = AlertNotice(1, "Main", 80, 8, 10, 2, null)

    @Test fun disabled_notifications_do_not_consume_alerts() = runBlocking {
        var evaluated = 0
        AlertRunner(enabled = { false }, evaluate = { evaluated++; listOf(notice) }, post = { }).run()
        assertEquals(0, evaluated)
    }

    @Test fun every_notice_is_posted_once() = runBlocking {
        val posted = mutableListOf<AlertNotice>()
        AlertRunner(enabled = { true }, evaluate = { listOf(notice, notice.copy(planId = 2)) }, post = { posted += it }).run()
        assertEquals(listOf(1L, 2L), posted.map { it.planId })
    }
}
