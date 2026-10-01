package io.github.khaledbahaaeldin.emberbyte

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.khaledbahaaeldin.emberbyte.home.HomeScreen
import io.github.khaledbahaaeldin.emberbyte.nav.Destination
import io.github.khaledbahaaeldin.emberbyte.placeholder.PlaceholderScreen
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.FloatingPillNavBar
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.glassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberGlassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberNavBarVisibility

@Composable
fun EmberbyteApp(graph: AppGraph, hapticsEnabled: Boolean = true) {
    val navController = rememberNavController()
    val visibility = rememberNavBarVisibility()
    val glass = rememberGlassSource()
    val backStack by navController.currentBackStackEntryAsState()
    val selected = Destination.fromRoute(backStack?.destination?.route)
    val items = remember { Destination.entries.map { it.toNavBarItem() } }

    Box(Modifier.fillMaxSize().nestedScroll(visibility.connection)) {
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.fillMaxSize().glassSource(glass),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(viewModel(factory = graph.homeViewModelFactory()))
            }
            composable(Destination.Apps.route) {
                PlaceholderScreen("Apps", "Per-app usage arrives in the next milestone.")
            }
            composable(Destination.Plans.route) {
                PlaceholderScreen("Plans", "The flexible plan editor arrives in milestone 3.")
            }
            composable(Destination.Lens.route) {
                PlaceholderScreen("Lens", "Live Lens is opt-in and arrives in milestone 5.")
            }
        }
        FloatingPillNavBar(
            items = items,
            selectedId = selected.id,
            onSelect = { id ->
                navController.navigate(Destination.fromId(id).route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            visible = visibility.visible,
            hapticsEnabled = hapticsEnabled,
            glass = glass,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}
