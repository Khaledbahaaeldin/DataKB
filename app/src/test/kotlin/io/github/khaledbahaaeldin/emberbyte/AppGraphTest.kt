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

    @Test fun the_graph_provides_working_components_and_factory() = runBlocking {
        val graph = AppGraph(app)
        // Permissions initial state is observable
        val perms = graph.permissions.observe().first()
        org.junit.Assert.assertNotNull(perms)
        assertEquals(false, perms.vpnConsentGranted)

        // Prune executes without error on real Room store
        graph.prune()

        // HomeViewModel factory creates instance
        val factory = graph.homeViewModelFactory()
        val viewModel = factory.create(
            io.github.khaledbahaaeldin.emberbyte.home.HomeViewModel::class.java,
            androidx.lifecycle.viewmodel.CreationExtras.Empty,
        )
        org.junit.Assert.assertNotNull(viewModel)
        org.junit.Assert.assertNotNull(viewModel.uiState.value)
    }

    @Test fun emberbyte_application_exposes_singleton_graph() {
        val emberbyteApp = app as EmberbyteApplication
        org.junit.Assert.assertNotNull(emberbyteApp.graph)
        org.junit.Assert.assertSame(emberbyteApp.graph, emberbyteApp.graph)
    }
}
