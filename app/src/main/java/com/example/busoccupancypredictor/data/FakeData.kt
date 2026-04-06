package com.example.busoccupancypredictor.data

import com.example.busoccupancypredictor.domain.*

object FakeData {
    val cities = listOf(
        City("almaty", "Алматы", 43.238949, 76.889709, 12.5),
        City("astana", "Астана", 51.169392, 71.449074, 12.0),
        City("shymkent", "Шымкент", 42.315514, 69.586907, 12.0)
    )

    val city = cities.first().name

    val routes = listOf(
        RouteItem(5, "пос. Ак-Тобе — Абдулинных"),
        RouteItem(10, "Метро Райымбек батыра — кольцевой"),
        RouteItem(12, "Медеу — Ж/д вокзал Алматы-2")
    )

    fun loadNow(routeId: Int): LoadNow = when (routeId) {
        10 -> LoadNow(SeatStatus.FEW, 58)
        12 -> LoadNow(SeatStatus.NONE, 74)
        else -> LoadNow(SeatStatus.AVAILABLE, 28)
    }

    fun stops(routeId: Int): List<StopItem> = when (routeId) {
        10 -> listOf(
            StopItem("Метро Райымбек батыра", SeatStatus.FEW),
            StopItem("Центральная", SeatStatus.FEW),
            StopItem("Станция B", SeatStatus.NONE),
            StopItem("Станция C", SeatStatus.AVAILABLE)
        )
        else -> listOf(
            StopItem("Центральная", SeatStatus.FEW),
            StopItem("Орбита", SeatStatus.AVAILABLE),
            StopItem("Саяхат", SeatStatus.FEW)
        )
    }

    fun forecast(routeId: Int): List<ForecastPoint> = when (routeId) {
        10 -> listOf(
            ForecastPoint(10, SeatStatus.FEW, 60),
            ForecastPoint(20, SeatStatus.NONE, 72),
            ForecastPoint(30, SeatStatus.NONE, 82),
            ForecastPoint(60, SeatStatus.AVAILABLE, 28)
        )
        else -> listOf(
            ForecastPoint(10, SeatStatus.FEW, 55),
            ForecastPoint(20, SeatStatus.FEW, 62),
            ForecastPoint(30, SeatStatus.NONE, 75),
            ForecastPoint(60, SeatStatus.AVAILABLE, 35)
        )
    }

    fun timeSummary(routeId: Int): List<TimeSummary> = listOf(
        TimeSummary("Утро", "07–11", SeatStatus.NONE),
        TimeSummary("Обед", "12–16", SeatStatus.FEW, highlighted = true),
        TimeSummary("Вечер", "17–21", SeatStatus.NONE)
    )

    fun hours(routeId: Int): List<HourLoad> = listOf(
        HourLoad(8, SeatStatus.NONE),
        HourLoad(10, SeatStatus.FEW),
        HourLoad(12, SeatStatus.FEW),
        HourLoad(14, SeatStatus.NONE),
        HourLoad(16, SeatStatus.NONE),
        HourLoad(18, SeatStatus.NONE),
        HourLoad(20, SeatStatus.FEW),
        HourLoad(22, SeatStatus.AVAILABLE)
    )

    fun busyStops(routeId: Int): List<StopFrequency> = listOf(
        StopFrequency("Центральная", SeatStatus.NONE, 78),
        StopFrequency("Метро Райымбек батыра", SeatStatus.FEW, 64),
        StopFrequency("Станция B", SeatStatus.NONE, 70),
        StopFrequency("Саяхат", SeatStatus.FEW, 59),
        StopFrequency("Орбита", SeatStatus.AVAILABLE, 41)
    )

    fun buses(cityId: String): List<BusPosition> = when (cityId) {
        "almaty" -> listOf(
            BusPosition("A-10-1", 10, "almaty", 43.2516, 76.9323, SeatStatus.FEW),
            BusPosition("A-10-2", 10, "almaty", 43.2352, 76.9151, SeatStatus.NONE),
            BusPosition("A-12-1", 12, "almaty", 43.2689, 76.8732, SeatStatus.AVAILABLE),
            BusPosition("A-12-2", 12, "almaty", 43.2228, 76.8510, SeatStatus.FEW)
        )
        "astana" -> listOf(
            BusPosition("N-10-1", 10, "astana", 51.1653, 71.4704, SeatStatus.FEW),
            BusPosition("N-12-1", 12, "astana", 51.1551, 71.4306, SeatStatus.AVAILABLE)
        )
        else -> listOf(
            BusPosition("S-10-1", 10, "shymkent", 42.3199, 69.5903, SeatStatus.NONE)
        )
    }
}