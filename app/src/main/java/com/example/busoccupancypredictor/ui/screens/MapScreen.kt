package com.example.busoccupancypredictor.ui.screens

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.busoccupancypredictor.data.FakeData
import com.example.busoccupancypredictor.data.RoutesRepository
import com.example.busoccupancypredictor.data.StopsRepository
import com.example.busoccupancypredictor.domain.BusRouteMapItem
import com.example.busoccupancypredictor.domain.BusStopMapItem
import com.example.busoccupancypredictor.domain.City
import com.example.busoccupancypredictor.domain.RoutePathPoint
import com.example.busoccupancypredictor.R
import com.example.busoccupancypredictor.ui.Brand
import com.example.busoccupancypredictor.ui.components.Segmented
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val MIN_STOPS_ZOOM = 14.0
private const val MAX_STOPS_LAT_SPAN = 0.08
private val MAP_INFO_CARD_MAX_WIDTH = 220.dp
private const val SIMULATION_TICK_MS = 90L
private const val MARKER_SIZE_PX = 44

private data class SimulatedRouteTrack(
    val directionId: String,
    val routeNumber: String,
    val points: List<RoutePathPoint>,
    val cumulativeDistances: DoubleArray,
    val totalDistanceMeters: Double,
    val stopProgresses: List<Double>,
    val busCount: Int,
    val cycleMillis: Long,
    val phaseOffset: Double
)

private data class SimulatedBusMapItem(
    val id: String,
    val routeNumber: String,
    val lat: Double,
    val lon: Double
)

@Composable
fun MapScreen(
    initialRouteId: String = "",
    initialRouteRequestNonce: Int = 0,
    onRouteActivated: (BusRouteMapItem) -> Unit = {},
    onRouteCleared: () -> Unit = {},
    onInitialRouteHandled: (String, Int) -> Unit = { _, _ -> }
) {
    var showRoutesMode by rememberSaveable(initialRouteRequestNonce) { mutableStateOf(true) }
    var recenterKey by remember { mutableStateOf(0) }
    var canShowStops by remember { mutableStateOf(false) }
    var visibleStopsCount by remember { mutableStateOf(0) }

    val cities = remember { FakeData.cities }

    val context = LocalContext.current
    val allStops = remember {
        runCatching { StopsRepository.loadStops(context) }.getOrDefault(emptyList())
    }
    val allRoutes = remember {
        runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    }
    val initialRoute = remember(initialRouteId, initialRouteRequestNonce, allRoutes) {
        if (initialRouteId.isNotBlank() && initialRouteRequestNonce > 0) {
            allRoutes.firstOrNull { it.id == initialRouteId }
        } else {
            null
        }
    }
    var cityId by rememberSaveable(initialRouteRequestNonce) { mutableStateOf(initialRoute?.cityId ?: "almaty") }

    val cityRoutes = remember(cityId, allRoutes) {
        allRoutes
            .filter { it.cityId == cityId }
            .sortedWith(compareBy<BusRouteMapItem>({ routeSortGroup(it.number) }, { routeSortSuffix(it.number) }))
    }
    val cityStops = remember(cityId, allStops) { allStops.filter { it.cityId == cityId } }

    var query by rememberSaveable(initialRouteRequestNonce) { mutableStateOf(initialRoute?.number.orEmpty()) }
    var selectedRouteId by rememberSaveable(initialRouteRequestNonce) { mutableStateOf(initialRoute?.id.orEmpty()) }
    var lastHandledInitialRequest by rememberSaveable { mutableStateOf(0) }
    var simulationTickMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(initialRouteId, initialRouteRequestNonce, allRoutes) {
        if (
            initialRouteId.isNotBlank() &&
            initialRouteRequestNonce > 0 &&
            initialRouteRequestNonce != lastHandledInitialRequest
        ) {
            allRoutes.firstOrNull { it.id == initialRouteId }?.let { route ->
                cityId = route.cityId
                selectedRouteId = route.id
                query = route.number
                showRoutesMode = true
                recenterKey++
                onRouteActivated(route)
            }
            lastHandledInitialRequest = initialRouteRequestNonce
            onInitialRouteHandled(initialRouteId, initialRouteRequestNonce)
        }
    }

    val selectedRoute = remember(selectedRouteId, cityRoutes) {
        cityRoutes.firstOrNull { it.id == selectedRouteId }
    }
    LaunchedEffect(selectedRoute?.id) {
        if (selectedRoute != null) {
            onRouteActivated(selectedRoute)
        } else {
            onRouteCleared()
        }
    }

    LaunchedEffect(cityId) {
        if (selectedRouteId.isNotBlank() && cityRoutes.none { it.id == selectedRouteId }) {
            selectedRouteId = ""
            query = ""
        }
    }

    val searchResults = remember(query, cityRoutes) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            val needle = normalizedQuery.lowercase()
            cityRoutes.filter { route ->
                route.number.lowercase().contains(needle) ||
                    route.fromName.lowercase().contains(needle) ||
                    route.toName.lowercase().contains(needle) ||
                    route.description.lowercase().contains(needle)
            }.take(8)
        }
    }

    LaunchedEffect(query, cityRoutes) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            selectedRouteId = ""
        } else {
            val exact = cityRoutes.singleOrNull { it.number.lowercase() == normalizedQuery }
            if (exact != null) {
                selectedRouteId = exact.id
            } else if (selectedRoute?.number?.lowercase() != normalizedQuery) {
                selectedRouteId = ""
            }
        }
    }

    val displayedRoutes = selectedRoute?.let(::listOf).orEmpty()
    val displayedStops = remember(selectedRoute, cityStops) {
        selectedRoute?.let { route ->
            cityStops.filter { stop -> stop.routeNumbers.contains(route.number) }
        }.orEmpty()
    }
    val simulationTracks = remember(selectedRoute) {
        selectedRoute?.let(::buildSimulationTracks).orEmpty()
    }
    LaunchedEffect(selectedRoute?.id, simulationTracks) {
        if (selectedRoute == null || simulationTracks.isEmpty()) {
            return@LaunchedEffect
        }
        while (true) {
            simulationTickMillis = SystemClock.elapsedRealtime()
            delay(SIMULATION_TICK_MS)
        }
    }
    val displayedBuses = remember(simulationTracks, simulationTickMillis) {
        simulateBuses(simulationTracks, simulationTickMillis)
    }

    val selectedCity = cities.firstOrNull { it.id == cityId } ?: cities.first()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Карта", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        CityPicker(
            cities = cities,
            selectedId = cityId,
            onSelect = { cityId = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        RouteSearchField(
            query = query,
            results = searchResults,
            selectedRoute = selectedRoute,
            onQueryChange = { newValue -> query = newValue.take(40) },
            onRouteSelected = { route ->
                cityId = route.cityId
                selectedRouteId = route.id
                query = route.number
                recenterKey++
            },
            onClear = {
                query = ""
                selectedRouteId = ""
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Segmented(
            left = "Маршруты",
            right = "Загруженность",
            selectedLeft = showRoutesMode,
            onSelectLeft = { showRoutesMode = true },
            onSelectRight = { showRoutesMode = false },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brand.GrayBg)
        ) {
            OsmMap(
                city = selectedCity,
                buses = displayedBuses,
                stops = displayedStops,
                routes = displayedRoutes,
                showRoutesMode = showRoutesMode,
                recenterKey = recenterKey,
                onStopsVisibilityChanged = { isVisible, count ->
                    canShowStops = isVisible
                    visibleStopsCount = count
                },
                modifier = Modifier.fillMaxSize()
            )

            when {
                selectedRoute == null -> {
                    HintCard(
                        title = "Найдите маршрут",
                        message = "Введите номер маршрута или направление, затем выберите его из результатов поиска.",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }

                showRoutesMode && canShowStops -> {
                    StopsLegendCard(
                        visibleStopsCount = visibleStopsCount,
                        totalStopsCount = displayedStops.size,
                        routeNumber = selectedRoute.number,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }

                showRoutesMode -> {
                    HintCard(
                        title = "Приблизьте карту",
                        message = "Остановки выбранного маршрута появятся, когда карта будет ближе.",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }

                else -> {
                    LoadLegendCard(
                        routeNumber = selectedRoute.number,
                        busesCount = displayedBuses.size,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brand.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    null,
                    tint = Brand.Yellow,
                    modifier = Modifier.clickable { recenterKey++ }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityPicker(
    cities: List<City>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val selected = cities.first { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = !open },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Город") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city.name) },
                    onClick = {
                        onSelect(city.id)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RouteSearchField(
    query: String,
    results: List<BusRouteMapItem>,
    selectedRoute: BusRouteMapItem?,
    onQueryChange: (String) -> Unit,
    onRouteSelected: (BusRouteMapItem) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSelection by rememberUpdatedState(selectedRoute)

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Поиск по номеру или направлению маршрута") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Brand.GrayText) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Close, null)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (query.isNotBlank() && currentSelection == null) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
            ) {
                if (results.isEmpty()) {
                    Text(
                        "Ничего не найдено",
                        modifier = Modifier.padding(14.dp),
                        color = Brand.GrayText
                    )
                } else {
                    results.forEach { route ->
                        RouteSearchResultRow(
                            route = route,
                            onClick = { onRouteSelected(route) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSearchResultRow(route: BusRouteMapItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brand.GrayBg),
            contentAlignment = Alignment.Center
        ) {
            Text(route.number, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f)) {
            Text(route.number, fontWeight = FontWeight.Bold)
            Text(routeSubtitle(route), color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OsmMap(
    city: City,
    buses: List<SimulatedBusMapItem>,
    stops: List<BusStopMapItem>,
    routes: List<BusRouteMapItem>,
    showRoutesMode: Boolean,
    recenterKey: Int,
    onStopsVisibilityChanged: (Boolean, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapViewportVersion by remember { mutableStateOf(0) }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(city.zoom)
            controller.setCenter(GeoPoint(city.lat, city.lon))
        }
    }

    LaunchedEffect(city.id, recenterKey, routes.map { it.id }) {
        if (routes.isNotEmpty()) {
            val allPoints = routes.flatMap { route -> route.directions.flatMap { direction -> direction.pathPoints } }.toGeoPoints()
            if (allPoints.isNotEmpty()) {
                mapView.zoomToBoundingBox(allPoints.toBoundingBox(), true, 96)
            }
        } else {
            mapView.controller.setZoom(city.zoom)
            mapView.controller.animateTo(GeoPoint(city.lat, city.lon))
        }
        mapViewportVersion++
    }

    LaunchedEffect(buses, stops, routes, showRoutesMode, mapViewportVersion) {
        mapView.overlays.filterIsInstance<Marker>().forEach { mapView.overlays.remove(it) }
        mapView.overlays.filterIsInstance<Polyline>().forEach { mapView.overlays.remove(it) }

        routes.forEach { route ->
            route.directions.forEach { direction ->
                val points = direction.pathPoints.toGeoPoints()
                if (points.size < 2) return@forEach

                val polyline = Polyline().apply {
                    setPoints(points)
                    outlinePaint.color = routeColor(route.number).toArgb()
                    outlinePaint.strokeWidth = if (showRoutesMode) 8f else 6f
                    title = "Маршрут ${route.number}"
                    subDescription = routeSubtitle(route)
                }
                mapView.overlays.add(polyline)
            }
        }

        if (showRoutesMode && routes.isNotEmpty()) {
            val bounds = mapView.boundingBox
            val latSpan = bounds?.let { it.latNorth - it.latSouth } ?: Double.MAX_VALUE
            val canDisplayStops = mapView.zoomLevelDouble >= MIN_STOPS_ZOOM && latSpan <= MAX_STOPS_LAT_SPAN
            val visibleStops = if (canDisplayStops && bounds != null) {
                stops.filter { it.isInside(bounds) }
            } else {
                emptyList()
            }
            val visibleBuses = if (canDisplayStops && bounds != null) {
                buses.filter { it.isInside(bounds) }
            } else {
                emptyList()
            }

            visibleStops.forEach { stop ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(stop.lat, stop.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = stop.name
                    subDescription = stop.routeNumbers.joinToString(", ")
                    icon = stopIcon()
                }
                mapView.overlays.add(marker)
            }

            visibleBuses.forEach { bus ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(bus.lat, bus.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Маршрут ${bus.routeNumber}"
                    icon = busIcon(context)
                }
                mapView.overlays.add(marker)
            }

            onStopsVisibilityChanged(canDisplayStops, visibleStops.size)
        } else {
            buses.forEach { bus ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(bus.lat, bus.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Маршрут ${bus.routeNumber}"
                    icon = busIcon(context)
                }
                mapView.overlays.add(marker)
            }
            onStopsVisibilityChanged(false, 0)
        }

        mapView.invalidate()
    }

    DisposableEffect(lifecycle, mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapViewportVersion++
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapViewportVersion++
                return false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }

        mapView.addMapListener(mapListener)
        lifecycle.addObserver(observer)

        onDispose {
            mapView.removeMapListener(mapListener)
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun buildSimulationTracks(route: BusRouteMapItem): List<SimulatedRouteTrack> {
    return route.directions.mapIndexedNotNull { index, direction ->
        val points = direction.pathPoints
        if (points.size < 2) return@mapIndexedNotNull null

        val cumulativeDistances = buildCumulativeDistances(points)
        val totalDistanceMeters = cumulativeDistances.lastOrNull() ?: 0.0
        if (totalDistanceMeters <= 0.0) return@mapIndexedNotNull null

        SimulatedRouteTrack(
            directionId = direction.directionId,
            routeNumber = route.number,
            points = points,
            cumulativeDistances = cumulativeDistances,
            totalDistanceMeters = totalDistanceMeters,
            stopProgresses = buildStopProgresses(points, cumulativeDistances, totalDistanceMeters, direction.platforms),
            busCount = simulatedBusCount(direction.platforms.size),
            cycleMillis = simulatedCycleMillis(totalDistanceMeters),
            phaseOffset = (index * 0.17) % 1.0
        )
    }
}

private fun simulateBuses(
    tracks: List<SimulatedRouteTrack>,
    tickMillis: Long
): List<SimulatedBusMapItem> {
    return tracks.flatMap { track ->
        List(track.busCount) { busIndex ->
            val loopProgress =
                ((tickMillis % track.cycleMillis).toDouble() / track.cycleMillis + track.phaseOffset + busIndex.toDouble() / track.busCount) % 1.0
            val rawProgress = if (loopProgress <= 0.5) {
                loopProgress * 2.0
            } else {
                (1.0 - loopProgress) * 2.0
            }
            val progress = applyStopDwell(track, rawProgress)
            val point = interpolatePoint(track, progress)
            SimulatedBusMapItem(
                id = "${track.directionId}-$busIndex",
                routeNumber = track.routeNumber,
                lat = point.lat,
                lon = point.lon
            )
        }
    }
}

private fun buildCumulativeDistances(points: List<RoutePathPoint>): DoubleArray {
    val cumulative = DoubleArray(points.size)
    for (index in 1 until points.size) {
        cumulative[index] = cumulative[index - 1] + distanceMeters(points[index - 1], points[index])
    }
    return cumulative
}

private fun buildStopProgresses(
    points: List<RoutePathPoint>,
    cumulativeDistances: DoubleArray,
    totalDistanceMeters: Double,
    platforms: List<com.example.busoccupancypredictor.domain.RoutePlatformMapItem>
): List<Double> {
    if (platforms.isEmpty() || totalDistanceMeters <= 0.0) return emptyList()

    return platforms.mapNotNull { platform ->
        val nearestIndex = points.indices.minByOrNull { index ->
            squaredDistance(points[index].lat, points[index].lon, platform.lat, platform.lon)
        } ?: return@mapNotNull null

        (cumulativeDistances[nearestIndex] / totalDistanceMeters)
            .coerceIn(0.0, 1.0)
    }.distinct().sorted()
}

private fun simulatedBusCount(platformCount: Int): Int {
    return when {
        platformCount >= 50 -> 5
        platformCount >= 30 -> 4
        platformCount >= 16 -> 3
        else -> 2
    }
}

private fun simulatedCycleMillis(totalDistanceMeters: Double): Long {
    val base = 600_000L + (totalDistanceMeters * 1.4).toLong()
    return base.coerceIn(540_000L, 1_080_000L)
}

private fun interpolatePoint(track: SimulatedRouteTrack, progress: Double): RoutePathPoint {
    val targetDistance = track.totalDistanceMeters * progress.coerceIn(0.0, 1.0)
    val segmentIndex = track.cumulativeDistances.indexOfFirst { it >= targetDistance }
    if (segmentIndex <= 0) return track.points.first()

    val startPoint = track.points[segmentIndex - 1]
    val endPoint = track.points[segmentIndex]
    val startDistance = track.cumulativeDistances[segmentIndex - 1]
    val endDistance = track.cumulativeDistances[segmentIndex]
    val segmentDistance = (endDistance - startDistance).coerceAtLeast(0.0001)
    val fraction = ((targetDistance - startDistance) / segmentDistance).coerceIn(0.0, 1.0)

    return RoutePathPoint(
        lon = startPoint.lon + (endPoint.lon - startPoint.lon) * fraction,
        lat = startPoint.lat + (endPoint.lat - startPoint.lat) * fraction
    )
}

private fun applyStopDwell(track: SimulatedRouteTrack, progress: Double): Double {
    if (track.stopProgresses.isEmpty()) return progress.coerceIn(0.0, 1.0)

    val dwellFraction = minOf(0.009, 0.42 / track.stopProgresses.size)
    val totalDwell = dwellFraction * track.stopProgresses.size
    val effectiveMotionFraction = (1.0 - totalDwell).coerceAtLeast(0.1)
    val current = progress.coerceIn(0.0, 1.0)
    var spentInDwells = 0.0

    track.stopProgresses.forEach { stopProgress ->
        val pauseStart = stopProgress * effectiveMotionFraction + spentInDwells
        val pauseEnd = pauseStart + dwellFraction

        if (current < pauseStart) {
            return ((current - spentInDwells) / effectiveMotionFraction).coerceIn(0.0, 1.0)
        }

        if (current <= pauseEnd) {
            return stopProgress
        }

        spentInDwells += dwellFraction
    }

    return ((current - spentInDwells) / effectiveMotionFraction).coerceIn(0.0, 1.0)
}

private fun distanceMeters(from: RoutePathPoint, to: RoutePathPoint): Double {
    val latMeters = (to.lat - from.lat) * 111_320.0
    val lonMeters = (to.lon - from.lon) * 111_320.0 * kotlin.math.cos(Math.toRadians((from.lat + to.lat) / 2.0))
    return kotlin.math.hypot(latMeters, lonMeters)
}

private fun squaredDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = lat1 - lat2
    val dLon = lon1 - lon2
    return dLat * dLat + dLon * dLon
}

private fun busIcon(context: Context): LayerDrawable {
    val background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Brand.Yellow.toArgb())
        setStroke(4, Brand.Black.toArgb())
        setSize(MARKER_SIZE_PX, MARKER_SIZE_PX)
    }
    val busDrawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_map_bus)) {
        "Bus marker drawable is missing"
    }.mutate()

    return LayerDrawable(arrayOf(background, busDrawable)).apply {
        setLayerInset(1, 9, 9, 9, 9)
        setBounds(0, 0, MARKER_SIZE_PX, MARKER_SIZE_PX)
    }
}

private fun stopIcon(): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(Brand.Yellow.toArgb())
    setStroke(6, Brand.Black.toArgb())
    setSize(MARKER_SIZE_PX, MARKER_SIZE_PX)
}

private fun routeColor(routeNumber: String): Color {
    val palette = listOf(
        Color(0xFF0072CE),
        Color(0xFFE67E22),
        Color(0xFF2E8B57),
        Color(0xFFC0392B),
        Color(0xFF6C5CE7),
        Color(0xFF008B8B)
    )
    val index = (routeNumber.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

@Composable
private fun StopsLegendCard(
    visibleStopsCount: Int,
    totalStopsCount: Int,
    routeNumber: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .widthIn(max = MAP_INFO_CARD_MAX_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        Text("Маршрут $routeNumber", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "$visibleStopsCount из $totalStopsCount остановок на экране",
            color = Brand.GrayText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LoadLegendCard(
    routeNumber: String,
    busesCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .widthIn(max = MAP_INFO_CARD_MAX_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        Text("Маршрут $routeNumber", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (busesCount > 0) "Автобусов на карте: $busesCount" else "Для этого маршрута автобусы пока не добавлены",
            color = Brand.GrayText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HintCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .widthIn(max = MAP_INFO_CARD_MAX_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
    }
}

private fun BusStopMapItem.isInside(bounds: BoundingBox): Boolean {
    val latInside = lat in bounds.latSouth..bounds.latNorth
    val lonInside = if (bounds.lonWest <= bounds.lonEast) {
        lon in bounds.lonWest..bounds.lonEast
    } else {
        lon >= bounds.lonWest || lon <= bounds.lonEast
    }
    return latInside && lonInside
}

private fun SimulatedBusMapItem.isInside(bounds: BoundingBox): Boolean {
    val latInside = lat in bounds.latSouth..bounds.latNorth
    val lonInside = if (bounds.lonWest <= bounds.lonEast) {
        lon in bounds.lonWest..bounds.lonEast
    } else {
        lon >= bounds.lonWest || lon <= bounds.lonEast
    }
    return latInside && lonInside
}

private fun routeSubtitle(route: BusRouteMapItem): String {
    return listOf(route.fromName, route.toName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun routeSortGroup(value: String): Int {
    return value.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
}

private fun routeSortSuffix(value: String): String {
    return value.dropWhile { it.isDigit() }
}

private fun List<RoutePathPoint>.toGeoPoints(): List<GeoPoint> {
    return map { GeoPoint(it.lat, it.lon) }
}

private fun List<GeoPoint>.toBoundingBox(): BoundingBox {
    val north = maxOf { it.latitude }
    val south = minOf { it.latitude }
    val east = maxOf { it.longitude }
    val west = minOf { it.longitude }
    return BoundingBox(north, east, south, west)
}
