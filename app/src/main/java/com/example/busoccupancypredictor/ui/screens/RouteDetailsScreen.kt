package com.example.busoccupancypredictor.ui.screens

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.busoccupancypredictor.data.FakeData
import com.example.busoccupancypredictor.data.RoutesRepository
import com.example.busoccupancypredictor.domain.BusPosition
import com.example.busoccupancypredictor.domain.BusRouteMapItem
import com.example.busoccupancypredictor.domain.RoutePathPoint
import com.example.busoccupancypredictor.domain.RoutePlatformMapItem
import com.example.busoccupancypredictor.domain.SeatStatus
import com.example.busoccupancypredictor.ui.Brand
import com.example.busoccupancypredictor.ui.components.StatusPill
import com.example.busoccupancypredictor.ui.components.YellowOutlineCard
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

@Composable
fun RouteDetailsScreen(
    routeId: String,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenAnalytics: () -> Unit
) {
    val context = LocalContext.current
    val routes = remember {
        runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    }
    val route = remember(routeId, routes) { routes.firstOrNull { it.id == routeId } }

    if (route == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, null)
            }
            Text("Маршрут не найден", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Не удалось загрузить данные выбранного транспорта.", color = Brand.GrayText)
        }
        return
    }

    val fakeRouteId = route.number.toIntOrNull()?.takeIf { number ->
        FakeData.routes.any { it.id == number }
    }
    val loadNow = fakeRouteId?.let(FakeData::loadNow)
    val buses = remember(route.number, route.cityId) {
        FakeData.buses(route.cityId).filter { it.routeId.toString() == route.number }
    }
    val primaryDirection = remember(route.id) {
        route.directions.firstOrNull { it.directionType == "forward" } ?: route.directions.first()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, null)
                }
                Column(Modifier.weight(1f)) {
                    Text("Маршрут ${route.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(routeSubtitle(route), color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        AssistChip(
            onClick = {},
            label = { Text("${route.directions.size} направления") },
            colors = AssistChipDefaults.assistChipColors(containerColor = Brand.GrayBg)
        )

        Spacer(Modifier.height(10.dp))

        if (loadNow != null) {
            YellowOutlineCard {
                Text("Загрузка сейчас", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))

                StatusPill(
                    status = loadNow.status,
                    text = when (loadNow.status) {
                        SeatStatus.AVAILABLE -> "Сидячие есть"
                        SeatStatus.FEW -> "Средняя загрузка"
                        SeatStatus.NONE -> "Полная загрузка"
                    }
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    when (loadNow.status) {
                        SeatStatus.AVAILABLE -> "Есть свободные сидячие места"
                        SeatStatus.FEW -> "Свободных сидячих мест мало"
                        SeatStatus.NONE -> "Сидячих мест нет"
                    },
                    color = Brand.GrayText,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brand.GrayBorder)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .fillMaxWidth(loadNow.percent / 100f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Brand.Yellow)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${loadNow.percent}%", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        RealRouteMapCard(
            route = route,
            buses = buses,
            onOpenMap = onOpenMap,
            onOpenForecast = onOpenForecast,
            onOpenAnalytics = onOpenAnalytics
        )

        Spacer(Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Остановки маршрута", fontWeight = FontWeight.Bold)
                Text(
                    directionLabel(primaryDirection.directionType),
                    color = Brand.GrayText,
                    style = MaterialTheme.typography.bodySmall
                )

                val uniqueStops = primaryDirection.platforms.distinctBy { it.stationId.ifBlank { it.platformId } }
                uniqueStops.forEach { stop ->
                    StopRow(stop)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RealRouteMapCard(
    route: BusRouteMapItem,
    buses: List<BusPosition>,
    onOpenMap: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenAnalytics: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Text("Маршрут на карте", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brand.GrayBg)
        ) {
            RoutePreviewMap(
                route = route,
                buses = buses,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenMap,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть в карте")
            }
            Button(
                onClick = onOpenForecast,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть прогноз")
            }
            Button(
                onClick = onOpenAnalytics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть аналитику")
            }
        }
    }
}

@Composable
private fun RoutePreviewMap(
    route: BusRouteMapItem,
    buses: List<BusPosition>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
    }

    LaunchedEffect(route.id, buses) {
        mapView.overlays.clear()

        route.directions.forEach { direction ->
            val geoPoints = direction.pathPoints.toGeoPoints()
            if (geoPoints.size < 2) return@forEach

            val polyline = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = detailRouteColor(route.number).toArgb()
                outlinePaint.strokeWidth = 8f
                title = "Маршрут ${route.number}"
            }
            mapView.overlays.add(polyline)
        }

        buses.forEach { bus ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(bus.lat, bus.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Маршрут ${bus.routeId}"
                icon = detailBusIcon(bus.status)
            }
            mapView.overlays.add(marker)
        }

        val allPoints = route.directions.flatMap { it.pathPoints }.toGeoPoints()
        if (allPoints.isNotEmpty()) {
            mapView.zoomToBoundingBox(allPoints.toBoundingBox(), true, 64)
        }
        mapView.invalidate()
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun StopRow(stop: RoutePlatformMapItem) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Brand.Yellow)
        )
        Text(stop.name)
    }
}

private fun routeSubtitle(route: BusRouteMapItem): String {
    return listOf(route.fromName, route.toName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun directionLabel(directionType: String): String {
    return when (directionType.lowercase()) {
        "forward" -> "Прямое направление"
        "backward" -> "Обратное направление"
        else -> "Направление маршрута"
    }
}

private fun detailRouteColor(routeNumber: String): Color {
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

private fun detailBusIcon(status: SeatStatus): GradientDrawable {
    val color = when (status) {
        SeatStatus.AVAILABLE -> Brand.Green
        SeatStatus.FEW -> Brand.Yellow
        SeatStatus.NONE -> Brand.Red
    }

    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color.toArgb())
        setStroke(6, Brand.Black.toArgb())
        setSize(56, 56)
    }
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
