package com.example.busoccupancypredictor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.busoccupancypredictor.data.RoutesRepository
import com.example.busoccupancypredictor.ui.components.AppBottomBar
import com.example.busoccupancypredictor.ui.components.BottomTab
import com.example.busoccupancypredictor.ui.screens.AnalyticsDetailsScreen
import com.example.busoccupancypredictor.ui.screens.AnalyticsScreen
import com.example.busoccupancypredictor.ui.screens.ForecastScreen
import com.example.busoccupancypredictor.ui.screens.IntroScreen
import com.example.busoccupancypredictor.ui.screens.LoadingScreen
import com.example.busoccupancypredictor.ui.screens.MapScreen
import com.example.busoccupancypredictor.ui.screens.RouteDetailsScreen
import com.example.busoccupancypredictor.ui.screens.RoutesScreen

private enum class LaunchMode {
    BACKEND,
    LOCAL
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val routes = androidx.compose.runtime.remember {
        runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    }
    var pendingMapRouteId by rememberSaveable { mutableStateOf("") }
    var pendingMapRequestNonce by rememberSaveable { mutableStateOf(0) }
    var launchModeName by rememberSaveable { mutableStateOf(LaunchMode.BACKEND.name) }
    var selectedRouteId by rememberSaveable { mutableStateOf("") }
    var selectedRouteMapId by rememberSaveable { mutableStateOf("") }
    var selectedRouteLabel by rememberSaveable { mutableStateOf("") }
    var selectedDataRouteId by rememberSaveable { mutableStateOf(10) }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
        ?.substringBefore("/")
        ?.substringBefore("?")
        ?: "boot_loading"
    val launchMode = runCatching { LaunchMode.valueOf(launchModeName) }.getOrDefault(LaunchMode.BACKEND)

    val showBottom = current != "intro" && current != "loading" && current != "boot_loading"
    val showRouteInsights = selectedRouteId.isNotBlank()

    fun activateRoute(routeId: String) {
        selectedRouteId = routeId
        selectedRouteMapId = routeId
        val route = routes.firstOrNull { it.id == routeId }
        selectedRouteLabel = route?.number.orEmpty()
        selectedDataRouteId = route?.number?.toIntOrNull() ?: 10
    }

    fun clearActiveRoute() {
        selectedRouteId = ""
        selectedRouteMapId = ""
        selectedRouteLabel = ""
        selectedDataRouteId = 10
    }

    LaunchedEffect(showRouteInsights, current) {
        if (!showRouteInsights && current in listOf("forecast", "analytics", "analytics_details")) {
            nav.navigate("map") {
                popUpTo(nav.graph.startDestinationId) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (current == "intro" || current == "loading" || current == "boot_loading") {
        AppNavGraph(
            nav = nav,
            modifier = Modifier,
            pendingMapRouteId = pendingMapRouteId,
            pendingMapRequestNonce = pendingMapRequestNonce,
            launchMode = launchMode,
            selectedRouteMapId = selectedRouteMapId,
            selectedRouteLabel = selectedRouteLabel,
            selectedDataRouteId = selectedDataRouteId,
            activateRoute = ::activateRoute,
            clearActiveRoute = ::clearActiveRoute,
            onPendingMapRouteIdChange = { pendingMapRouteId = it },
            onPendingMapRequestNonceChange = { pendingMapRequestNonce = it },
            onLaunchModeChange = { launchModeName = it.name }
        )
    } else {
        Scaffold(
            bottomBar = {
                if (showBottom) {
                    val selected = when (current) {
                        "routes", "route" -> BottomTab.Routes.route
                        "map" -> BottomTab.Map.route
                        "forecast" -> BottomTab.Forecast.route
                        "analytics", "analytics_details" -> BottomTab.Analytics.route
                        else -> BottomTab.Routes.route
                    }
                    AppBottomBar(
                        selectedRoute = selected,
                        showRouteInsights = showRouteInsights
                    ) { tab ->
                        if (tab.route != selected) {
                            val targetRoute = when (tab) {
                                BottomTab.Forecast -> "forecast"
                                BottomTab.Analytics -> "analytics"
                                else -> tab.route
                            }
                            nav.navigate(targetRoute) {
                                popUpTo(nav.graph.startDestinationId) {
                                    inclusive = false
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            }
        ) { pad ->
            AppNavGraph(
                nav = nav,
                modifier = Modifier.padding(pad),
                pendingMapRouteId = pendingMapRouteId,
                pendingMapRequestNonce = pendingMapRequestNonce,
                launchMode = launchMode,
                selectedRouteMapId = selectedRouteMapId,
                selectedRouteLabel = selectedRouteLabel,
                selectedDataRouteId = selectedDataRouteId,
                activateRoute = ::activateRoute,
                clearActiveRoute = ::clearActiveRoute,
                onPendingMapRouteIdChange = { pendingMapRouteId = it },
                onPendingMapRequestNonceChange = { pendingMapRequestNonce = it },
                onLaunchModeChange = { launchModeName = it.name }
            )
        }
    }
}

@Composable
private fun AppNavGraph(
    nav: androidx.navigation.NavHostController,
    modifier: Modifier,
    pendingMapRouteId: String,
    pendingMapRequestNonce: Int,
    launchMode: LaunchMode,
    selectedRouteMapId: String,
    selectedRouteLabel: String,
    selectedDataRouteId: Int,
    activateRoute: (String) -> Unit,
    clearActiveRoute: () -> Unit,
    onPendingMapRouteIdChange: (String) -> Unit,
    onPendingMapRequestNonceChange: (Int) -> Unit,
    onLaunchModeChange: (LaunchMode) -> Unit
) {
    NavHost(
        navController = nav,
        startDestination = "boot_loading",
        modifier = modifier
    ) {
        composable("boot_loading") {
            LoadingScreen(
                onFinished = {
                    nav.navigate("intro") {
                        popUpTo("boot_loading") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("intro") {
            IntroScreen(
                onStartBackend = {
                    onLaunchModeChange(LaunchMode.BACKEND)
                    nav.navigate("loading") { launchSingleTop = true }
                },
                onStartLocal = {
                    onLaunchModeChange(LaunchMode.LOCAL)
                    nav.navigate("loading") { launchSingleTop = true }
                }
            )
        }
        composable("loading") {
            LoadingScreen(
                onFinished = {
                    nav.navigate("routes") {
                        popUpTo("intro") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("routes") {
            RoutesScreen(
                onRouteClick = { id ->
                    activateRoute(id)
                    nav.navigate("route/$id")
                }
            )
        }
        composable(
            route = "route/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            RouteDetailsScreen(
                routeId = id,
                onBack = { nav.popBackStack() },
                onOpenMap = {
                    activateRoute(id)
                    onPendingMapRouteIdChange(id)
                    onPendingMapRequestNonceChange(pendingMapRequestNonce + 1)
                    nav.navigate("map") {
                        launchSingleTop = true
                    }
                },
                onOpenForecast = {
                    activateRoute(id)
                    nav.navigate("forecast") { launchSingleTop = true }
                },
                onOpenAnalytics = {
                    activateRoute(id)
                    nav.navigate("analytics") { launchSingleTop = true }
                }
            )
        }
        composable("map") {
            MapScreen(
                initialRouteId = pendingMapRouteId.ifBlank { selectedRouteMapId },
                initialRouteRequestNonce = when {
                    pendingMapRouteId.isNotBlank() -> pendingMapRequestNonce
                    selectedRouteMapId.isNotBlank() -> 1
                    else -> 0
                },
                onRouteActivated = { route ->
                    activateRoute(route.id)
                },
                onRouteCleared = {
                    clearActiveRoute()
                },
                onInitialRouteHandled = { handledId, handledNonce ->
                    if (pendingMapRouteId == handledId && pendingMapRequestNonce == handledNonce) {
                        onPendingMapRouteIdChange("")
                    }
                }
            )
        }
        composable("forecast") {
            ForecastScreen(
                routeMapId = selectedRouteMapId,
                routeId = selectedDataRouteId,
                routeLabel = selectedRouteLabel,
                useBackend = launchMode == LaunchMode.BACKEND,
                onBack = null
            )
        }
        composable("analytics") {
            AnalyticsScreen(
                routeMapId = selectedRouteMapId,
                routeId = selectedDataRouteId,
                routeLabel = selectedRouteLabel,
                useBackend = launchMode == LaunchMode.BACKEND,
                onOpenDetails = { nav.navigate("analytics_details") }
            )
        }
        composable("analytics_details") {
            AnalyticsDetailsScreen(
                routeMapId = selectedRouteMapId,
                routeId = selectedDataRouteId,
                routeLabel = selectedRouteLabel,
                useBackend = launchMode == LaunchMode.BACKEND,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
