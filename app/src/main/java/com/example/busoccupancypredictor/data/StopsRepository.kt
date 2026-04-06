package com.example.busoccupancypredictor.data

import android.content.Context
import com.example.busoccupancypredictor.domain.BusStopMapItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object StopsRepository {
    private const val STOPS_ASSET = "stops_2gis.json"

    @Volatile
    private var cachedStops: List<BusStopMapItem>? = null

    fun loadStops(context: Context): List<BusStopMapItem> {
        cachedStops?.let { return it }

        val parsed = context.assets.open(STOPS_ASSET).bufferedReader().use { reader ->
            val root = Json.parseToJsonElement(reader.readText()).jsonArray
            root.mapNotNull { element ->
                val obj = element.jsonObject
                val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null

                BusStopMapItem(
                    id = obj["stop_id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    cityId = "almaty",
                    name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    lat = lat,
                    lon = lon,
                    routeNumbers = obj["route_numbers"]
                        ?.jsonArray
                        ?.mapNotNull { routeNumber -> routeNumber.jsonPrimitive.content }
                        .orEmpty()
                )
            }
        }

        cachedStops = parsed
        return parsed
    }
}
