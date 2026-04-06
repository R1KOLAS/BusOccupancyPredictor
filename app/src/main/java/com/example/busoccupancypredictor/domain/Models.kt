package com.example.busoccupancypredictor.domain

enum class SeatStatus { AVAILABLE, FEW, NONE }

data class RouteItem(
    val id: Int,
    val fromTo: String
)

data class StopItem(
    val name: String,
    val status: SeatStatus
)

data class LoadNow(
    val status: SeatStatus,
    val percent: Int
)

data class ForecastPoint(
    val minutes: Int,
    val status: SeatStatus,
    val percent: Int
)

data class TimeSummary(
    val title: String,
    val timeRange: String,
    val status: SeatStatus,
    val highlighted: Boolean = false
)

data class HourLoad(
    val hour: Int,
    val status: SeatStatus
)

data class StopFrequency(
    val name: String,
    val status: SeatStatus,
    val percent: Int
)

data class City(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoom: Double
)

data class BusPosition(
    val id: String,
    val routeId: Int,
    val cityId: String,
    val lat: Double,
    val lon: Double,
    val status: SeatStatus
)

data class BusStopMapItem(
    val id: String,
    val cityId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val routeNumbers: List<String>
)

data class RoutePathPoint(
    val lon: Double,
    val lat: Double
)

data class RoutePlatformMapItem(
    val platformId: String,
    val stationId: String,
    val name: String,
    val lon: Double,
    val lat: Double
)

data class RouteDirectionMapItem(
    val directionId: String,
    val directionType: String,
    val pathPoints: List<RoutePathPoint>,
    val platforms: List<RoutePlatformMapItem>
)

data class BusRouteMapItem(
    val id: String,
    val cityId: String,
    val number: String,
    val fromName: String,
    val toName: String,
    val description: String,
    val directions: List<RouteDirectionMapItem>
)
