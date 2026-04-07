package com.example.busoccupancypredictor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object BackendForecastRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = linkedMapOf<String, BackendForecastResult>()
    internal val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    fun getCachedForecast(
        routeId: String,
        directionId: String,
        stopId: String,
        dayOfWeek: String,
        timeValue: String,
        weather: String
    ): BackendForecastResult? = synchronized(cache) {
        cache[cacheKey(routeId, directionId, stopId, dayOfWeek, timeValue, weather)]
    }

    suspend fun fetchForecast(
        routeId: String,
        directionId: String,
        stopId: String,
        dayOfWeek: String,
        timeValue: String,
        weather: String
    ): BackendForecastResult = withContext(Dispatchers.IO) {
        val requestJson = buildJsonObject {
            put("route_id", routeId)
            put("direction_id", directionId)
            put("stop_id", stopId)
            put("day_of_week", dayOfWeek)
            put("time_value", timeValue)
            put("weather", weather)
        }.toString()

        val request = Request.Builder()
            .url("${BackendServerConfig.baseUrl}predict")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Backend error ${response.code}: $body")
            }
            parseForecast(body).also { result ->
                synchronized(cache) {
                    cache[cacheKey(routeId, directionId, stopId, dayOfWeek, timeValue, weather)] = result
                }
            }
        }
    }

    private fun cacheKey(
        routeId: String,
        directionId: String,
        stopId: String,
        dayOfWeek: String,
        timeValue: String,
        weather: String
    ): String = listOf(routeId, directionId, stopId, dayOfWeek, timeValue, weather).joinToString("|")

    private fun parseForecast(raw: String): BackendForecastResult {
        val root = json.parseToJsonElement(raw).jsonObject
        val forecast = root["forecast"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val minutes = item["minutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val occupancy = item["occupancy_percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val seatStatus = item["seat_status"]?.jsonPrimitive?.content.orEmpty()
                BackendForecastPoint(minutes, occupancy, seatStatus)
            }
            .orEmpty()

        return BackendForecastResult(
            routeId = root["route_id"]?.jsonPrimitive?.content.orEmpty(),
            routeNumber = root["route_number"]?.jsonPrimitive?.content.orEmpty(),
            directionId = root["direction_id"]?.jsonPrimitive?.content.orEmpty(),
            directionLabel = root["direction_label"]?.jsonPrimitive?.content.orEmpty(),
            stopId = root["stop_id"]?.jsonPrimitive?.content.orEmpty(),
            stopName = root["stop_name"]?.jsonPrimitive?.content.orEmpty(),
            stopIndex = root["stop_index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            totalStops = root["total_stops"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            occupancyPercent = root["occupancy_percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            seatStatus = root["seat_status"]?.jsonPrimitive?.content.orEmpty(),
            forecast = forecast
        )
    }
}

data class BackendForecastResult(
    val routeId: String,
    val routeNumber: String,
    val directionId: String,
    val directionLabel: String,
    val stopId: String,
    val stopName: String,
    val stopIndex: Int,
    val totalStops: Int,
    val occupancyPercent: Int,
    val seatStatus: String,
    val forecast: List<BackendForecastPoint>
)

data class BackendForecastPoint(
    val minutes: Int,
    val occupancyPercent: Int,
    val seatStatus: String
)

object BackendServerConfig {
    const val baseUrl = "http://10.0.2.2:8000/"
}
