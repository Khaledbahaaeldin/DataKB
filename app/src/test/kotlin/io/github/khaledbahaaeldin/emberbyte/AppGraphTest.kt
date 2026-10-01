package io.github.khaledbahaaeldin.emberbyte

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.khaledbahaaeldin.emberbyte.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppGraphTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun the_graph_builds_and_serves_real_empty_data() = runBlocking {
        val graph = AppGraph(app)
        assertEquals(Settings(), graph.settings.observe().first())
        assertTrue(graph.plans.observePlans().first().isEmpty())
        assertEquals(0L, graph.usage.observeToday().first().totalBytes)
        assertEquals(false, graph.onboarding.observeCompleted().first { it != null })
    }
}
