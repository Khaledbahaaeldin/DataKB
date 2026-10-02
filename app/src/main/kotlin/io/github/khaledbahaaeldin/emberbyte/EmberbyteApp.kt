package io.github.khaledbahaaeldin.emberbyte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.khaledbahaaeldin.emberbyte.apps.AppDetailScreen
import io.github.khaledbahaaeldin.emberbyte.apps.AppDetailViewModel
import io.github.khaledbahaaeldin.emberbyte.apps.AppsScreen
import io.github.khaledbahaaeldin.emberbyte.apps.AppsViewModel
import io.github.khaledbahaaeldin.emberbyte.history.HistoryScreen
import io.github.khaledbahaaeldin.emberbyte.history.HistoryViewModel
import io.github.khaledbahaaeldin.emberbyte.home.HomeScreen
import io.github.khaledbahaaeldin.emberbyte.nav.Destination
import io.github.khaledbahaaeldin.emberbyte.nav.Routes
import io.github.khaledbahaaeldin.emberbyte.nav.rememberPermissionActions
import io.github.khaledbahaaeldin.emberbyte.nav.tabEnterTransition
import io.github.khaledbahaaeldin.emberbyte.nav.tabExitTransition
import io.github.khaledbahaaeldin.emberbyte.onboarding.OnboardingScreen
import io.github.khaledbahaaeldin.emberbyte.onboarding.OnboardingViewModel
import io.github.khaledbahaaeldin.emberbyte.placeholder.PlaceholderScreen
import io.github.khaledbahaaeldin.emberbyte.sampler.ServiceStarter
import io.github.khaledbahaaeldin.emberbyte.settings.SettingsScreen
import io.github.khaledbahaaeldin.emberbyte.settings.SettingsViewModel
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.FloatingPillNavBar
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.glassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberGlassSource
import io.github.khaledbahaaeldin.emberbyte.ui.design.navbar.rememberNavBarVisibility
import io.github.khaledbahaaeldin.emberbyte.ui.design.theme.LocalReduceMotion

@Composable
fun EmberbyteApp(graph: AppGraph, hapticsEnabled: Boolean = true) {
    val completed by graph.onboarding.observeCompleted().collectAsStateWithLifecycle(initialValue = null)
    val value = completed
    if (value == null) {
        // The stored value is still loading: draw only the background so the right start screen is chosen once.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        AppScaffold(graph, startOnboarding = !value, hapticsEnabled = hapticsEnabled)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppScaffold(graph: AppGraph, startOnboarding: Boolean, hapticsEnabled: Boolean) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val visibility = rememberNavBarVisibility()
    val glass = rememberGlassSource()
    val actions = rememberPermissionActions(graph)
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val selected = Destination.fromRoute(route)
    val items = remember { Destination.entries.map { it.toNavBarItem() } }
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current

    Box(Modifier.fillMaxSize().nestedScroll(visibility.connection)) {
        NavHost(
            navController = navController,
            startDestination = if (startOnboarding) Routes.ONBOARDING else Destination.Home.route,
            modifier = Modifier.fillMaxSize().glassSource(glass),
            enterTransition = { tabEnterTransition(motion, reduceMotion) },
            exitTransition = { tabExitTransition(motion, reduceMotion) },
            popEnterTransition = { tabEnterTransition(motion, reduceMotion) },
            popExitTransition = { tabExitTransition(motion, reduceMotion) },
        ) {
            composable(Routes.ONBOARDING) {
                val viewModel: OnboardingViewModel = viewModel(factory = graph.onboardingViewModelFactory())
                OnboardingScreen(viewModel, actions, onFinish = {
                    viewModel.finish {
                        ServiceStarter.start(context)
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                })
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    viewModel(factory = graph.homeViewModelFactory()),
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onPromptAction = actions::onPrompt,
                )
            }
            composable(Destination.Apps.route) {
                val viewModel: AppsViewModel = viewModel(factory = graph.appsViewModelFactory())
                AppsScreen(
                    viewModel,
                    onOpenApp = { packageName -> navController.navigate(Routes.appDetail(packageName)) },
                    onPromptAction = actions::onPrompt,
                )
            }
            composable(
                Routes.APP_DETAIL,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                val viewModel: AppDetailViewModel =
                    viewModel(key = "app-$packageName", factory = graph.appDetailViewModelFactory(packageName))
                AppDetailScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(Destination.Plans.route) {
                PlaceholderScreen("Plans", "The flexible plan editor arrives in milestone 3.")
            }
            composable(Destination.Lens.route) {
                PlaceholderScreen("Lens", "Live Lens is opt-in and arrives in milestone 5.")
            }
            composable(Routes.HISTORY) {
                val viewModel: HistoryViewModel = viewModel(factory = graph.historyViewModelFactory())
                HistoryScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = graph.settingsViewModelFactory())
                SettingsScreen(viewModel, onBack = { navController.popBackStack() })
            }
        }
        if (route != Routes.ONBOARDING) {
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
}
