package com.example.busoccupancypredictor.ui.screens

import android.content.Context
import com.example.busoccupancypredictor.data.BackendAnalyticsResult
import com.example.busoccupancypredictor.data.RoutesRepository
import com.example.busoccupancypredictor.domain.BusRouteMapItem
import com.example.busoccupancypredictor.domain.HourLoad
import com.example.busoccupancypredictor.domain.SeatStatus
import com.example.busoccupancypredictor.domain.StopFrequency
import com.example.busoccupancypredictor.domain.TimeSummary
import kotlin.math.roundToInt

internal data class RouteDirectionStat(
    val title: String,
    val stopCount: Int,
    val pathPointCount: Int,
    val status: SeatStatus,
    val percent: Int
)

internal data class DayLoadStat(
    val label: String,
    val status: SeatStatus,
    val percent: Int
)

internal data class AnalyticsBundle(
    val route: BusRouteMapItem?,
    val summary: List<TimeSummary>,
    val hours: List<HourLoad>,
    val busyStops: List<StopFrequency>,
    val weekdayStats: List<DayLoadStat>,
    val directionStats: List<RouteDirectionStat>
)

internal fun buildAnalyticsBundle(context: Context, routeMapId: String): AnalyticsBundle {
    val routes = runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    val route = routes.firstOrNull { it.id == routeMapId }
    return if (route == null) {
        AnalyticsBundle(
            route = null,
            summary = emptyList(),
            hours = emptyList(),
            busyStops = emptyList(),
            weekdayStats = emptyList(),
            directionStats = emptyList()
        )
    } else {
        AnalyticsBundle(
            route = route,
            summary = buildTimeSummary(route),
            hours = buildHourLoads(route),
            busyStops = buildBusyStops(route),
            weekdayStats = buildWeekdayStats(route),
            directionStats = buildDirectionStats(route)
        )
    }
}

internal fun buildAnalyticsBundle(
    context: Context,
    routeMapId: String,
    backend: BackendAnalyticsResult
): AnalyticsBundle {
    val routes = runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    val route = routes.firstOrNull { it.id == routeMapId }
    return AnalyticsBundle(
        route = route,
        summary = backend.summary.map {
            TimeSummary(
                title = it.title,
                timeRange = it.timeRange,
                status = statusFromBackend(it.status),
                highlighted = it.highlighted
            )
        },
        hours = backend.hours.map { HourLoad(hour = it.hour, status = statusFromBackend(it.status)) },
        busyStops = backend.busyStops.map {
            StopFrequency(
                name = it.name,
                status = statusFromBackend(it.status),
                percent = it.percent
            )
        },
        weekdayStats = backend.weekdayStats.map {
            DayLoadStat(
                label = it.label,
                status = statusFromBackend(it.status),
                percent = it.percent
            )
        },
        directionStats = backend.directionStats.map {
            RouteDirectionStat(
                title = it.title,
                stopCount = it.stopCount,
                pathPointCount = it.pathPointCount,
                status = statusFromBackend(it.status),
                percent = it.percent
            )
        }
    )
}

private fun buildTimeSummary(route: BusRouteMapItem): List<TimeSummary> {
    val hours = buildHourLoads(route)
    return listOf(
        TimeSummary("Утро", "07-11", aggregateStatus(hours.filter { it.hour in 7..11 }), highlighted = false),
        TimeSummary("День", "12-16", aggregateStatus(hours.filter { it.hour in 12..16 }), highlighted = true),
        TimeSummary("Вечер", "17-21", aggregateStatus(hours.filter { it.hour in 17..21 }), highlighted = false)
    )
}

private fun buildHourLoads(route: BusRouteMapItem): List<HourLoad> {
    val base = routeBaseLoad(route)
    return listOf(8, 10, 12, 14, 16, 18, 20, 22).map { hour ->
        val percent = when (hour) {
            8 -> base + 24
            10 -> base + 10
            12 -> base + 6
            14 -> base + 12
            16 -> base + 18
            18 -> base + 26
            20 -> base + 8
            else -> base - 10
        }.coerceIn(10, 95)
        HourLoad(hour, statusForPercent(percent))
    }
}

private fun buildBusyStops(route: BusRouteMapItem): List<StopFrequency> {
    val totalDirections = route.directions.size.coerceAtLeast(1)
    val stopCounts = linkedMapOf<String, Pair<String, Int>>()

    route.directions.forEachIndexed { directionIndex, direction ->
        val directionWeight = if (direction.directionType.equals("forward", ignoreCase = true)) 6 else 3
        direction.platforms.forEachIndexed { stopIndex, platform ->
            val key = platform.stationId.ifBlank { platform.name }
            val existing = stopCounts[key]
            val positionalLoad = stopLoadWeight(stopIndex, direction.platforms.size)
            val updated = (existing?.second ?: 0) + positionalLoad + directionWeight + directionIndex
            stopCounts[key] = platform.name to updated
        }
    }

    val maxScore = stopCounts.maxOfOrNull { it.value.second }?.coerceAtLeast(1) ?: 1
    return stopCounts.values
        .map { (name, score) ->
            val percent = (score * 100.0 / maxScore).roundToInt().coerceIn(20, 95)
            StopFrequency(name, statusForPercent(percent), percent)
        }
        .sortedByDescending { it.percent }
        .take((8 + totalDirections).coerceAtMost(14))
}

private fun buildWeekdayStats(route: BusRouteMapItem): List<DayLoadStat> {
    val base = routeBaseLoad(route)
    val pattern = listOf(
        "Пн" to 12,
        "Вт" to 10,
        "Ср" to 8,
        "Чт" to 9,
        "Пт" to 16,
        "Сб" to -8,
        "Вс" to -14
    )
    return pattern.map { (label, delta) ->
        val percent = (base + delta).coerceIn(10, 95)
        DayLoadStat(label, statusForPercent(percent), percent)
    }
}

private fun buildDirectionStats(route: BusRouteMapItem): List<RouteDirectionStat> {
    val maxStops = route.directions.maxOfOrNull { it.platforms.size }?.coerceAtLeast(1) ?: 1
    return route.directions.map { direction ->
        val percent = (direction.platforms.size * 100.0 / maxStops).roundToInt().coerceIn(28, 92)
        RouteDirectionStat(
            title = analyticsDirectionLabel(direction.directionType, route.fromName, route.toName),
            stopCount = direction.platforms.size,
            pathPointCount = direction.pathPoints.size,
            status = statusForPercent(percent),
            percent = percent
        )
    }
}

private fun routeBaseLoad(route: BusRouteMapItem): Int {
    val stopFactor = route.directions.maxOfOrNull { it.platforms.size } ?: 0
    val pathFactor = route.directions.maxOfOrNull { it.pathPoints.size } ?: 0
    val numberFactor = route.number.filter { it.isDigit() }.toIntOrNull()?.rem(9) ?: 0
    return (28 + stopFactor / 2 + pathFactor / 40 + numberFactor).coerceIn(20, 72)
}

private fun stopLoadWeight(index: Int, totalStops: Int): Int {
    if (totalStops <= 1) return 10
    val progress = index.toDouble() / (totalStops - 1)
    return when {
        progress < 0.18 -> 16
        progress < 0.38 -> 11
        progress < 0.7 -> 7
        progress < 0.88 -> 12
        else -> 15
    }
}

private fun aggregateStatus(items: List<HourLoad>): SeatStatus {
    val score = items.sumOf {
        when (it.status) {
            SeatStatus.AVAILABLE -> 1
            SeatStatus.FEW -> 2
            SeatStatus.NONE -> 3
        }
    }
    val average = score.toDouble() / items.size.coerceAtLeast(1)
    return when {
        average >= 2.5 -> SeatStatus.NONE
        average >= 1.6 -> SeatStatus.FEW
        else -> SeatStatus.AVAILABLE
    }
}

internal fun statusForPercent(percent: Int): SeatStatus = when {
    percent >= 70 -> SeatStatus.NONE
    percent >= 45 -> SeatStatus.FEW
    else -> SeatStatus.AVAILABLE
}

internal fun statusFromBackend(status: String): SeatStatus = when (status.lowercase()) {
    "none" -> SeatStatus.NONE
    "few" -> SeatStatus.FEW
    else -> SeatStatus.AVAILABLE
}

internal fun analyticsDirectionLabel(directionType: String, fromName: String, toName: String): String {
    return when (directionType.lowercase()) {
        "forward" -> if (toName.isNotBlank()) "В сторону $toName" else "Прямое направление"
        "backward" -> if (fromName.isNotBlank()) "В сторону $fromName" else "Обратное направление"
        else -> listOf(fromName, toName).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Маршрут" }
    }
}
