package com.example.busoccupancypredictor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.IOException

object BackendAnalyticsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = linkedMapOf<String, BackendAnalyticsResult>()

    fun getCachedAnalytics(routeId: String, weather: String = "clear"): BackendAnalyticsResult? = synchronized(cache) {
        cache[cacheKey(routeId, weather)]
    }

    suspend fun fetchAnalytics(routeId: String, weather: String = "clear"): BackendAnalyticsResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${BackendServerConfig.baseUrl}analytics/$routeId?weather=$weather")
                .get()
                .build()

            BackendForecastRepository.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Backend error ${response.code}: $body")
                }
                parseAnalytics(body).also { result ->
                    synchronized(cache) {
                        cache[cacheKey(routeId, weather)] = result
                    }
                }
            }
        }

    private fun cacheKey(routeId: String, weather: String): String = "$routeId|$weather"

    private fun parseAnalytics(raw: String): BackendAnalyticsResult {
        val root = json.parseToJsonElement(raw).jsonObject

        fun parseSummary() = root["summary"]
            ?.jsonArray
            ?.map { element ->
                val item = element.jsonObject
                BackendAnalyticsSummaryItem(
                    title = item["title"]?.jsonPrimitive?.content.orEmpty(),
                    timeRange = item["time_range"]?.jsonPrimitive?.content.orEmpty(),
                    status = item["status"]?.jsonPrimitive?.content.orEmpty(),
                    highlighted = item["highlighted"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                )
            }
            .orEmpty()

        fun parseHours() = root["hours"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val hour = item["hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                BackendAnalyticsHourItem(
                    hour = hour,
                    status = item["status"]?.jsonPrimitive?.content.orEmpty()
                )
            }
            .orEmpty()

        fun parseBusyStops() = root["busy_stops"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val percent = item["percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                BackendAnalyticsStopItem(
                    name = item["name"]?.jsonPrimitive?.content.orEmpty(),
                    status = item["status"]?.jsonPrimitive?.content.orEmpty(),
                    percent = percent
                )
            }
            .orEmpty()

        fun parseWeekdays() = root["weekday_stats"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val percent = item["percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                BackendAnalyticsDayItem(
                    label = item["label"]?.jsonPrimitive?.content.orEmpty(),
                    status = item["status"]?.jsonPrimitive?.content.orEmpty(),
                    percent = percent
                )
            }
            .orEmpty()

        fun parseDirections() = root["direction_stats"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val stopCount = item["stop_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val pathPointCount = item["path_point_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val percent = item["percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                BackendAnalyticsDirectionItem(
                    title = item["title"]?.jsonPrimitive?.content.orEmpty(),
                    stopCount = stopCount,
                    pathPointCount = pathPointCount,
                    status = item["status"]?.jsonPrimitive?.content.orEmpty(),
                    percent = percent
                )
            }
            .orEmpty()

        return BackendAnalyticsResult(
            routeId = root["route_id"]?.jsonPrimitive?.content.orEmpty(),
            routeNumber = root["route_number"]?.jsonPrimitive?.content.orEmpty(),
            summary = parseSummary(),
            hours = parseHours(),
            busyStops = parseBusyStops(),
            weekdayStats = parseWeekdays(),
            directionStats = parseDirections()
        )
    }
}

data class BackendAnalyticsResult(
    val routeId: String,
    val routeNumber: String,
    val summary: List<BackendAnalyticsSummaryItem>,
    val hours: List<BackendAnalyticsHourItem>,
    val busyStops: List<BackendAnalyticsStopItem>,
    val weekdayStats: List<BackendAnalyticsDayItem>,
    val directionStats: List<BackendAnalyticsDirectionItem>
)

data class BackendAnalyticsSummaryItem(
    val title: String,
    val timeRange: String,
    val status: String,
    val highlighted: Boolean
)

data class BackendAnalyticsHourItem(
    val hour: Int,
    val status: String
)

data class BackendAnalyticsStopItem(
    val name: String,
    val status: String,
    val percent: Int
)

data class BackendAnalyticsDayItem(
    val label: String,
    val status: String,
    val percent: Int
)

data class BackendAnalyticsDirectionItem(
    val title: String,
    val stopCount: Int,
    val pathPointCount: Int,
    val status: String,
    val percent: Int
)
