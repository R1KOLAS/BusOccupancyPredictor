package com.example.busoccupancypredictor.data

import android.content.Context
import com.example.busoccupancypredictor.domain.BusRouteMapItem
import com.example.busoccupancypredictor.domain.RouteDirectionMapItem
import com.example.busoccupancypredictor.domain.RoutePathPoint
import com.example.busoccupancypredictor.domain.RoutePlatformMapItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object RoutesRepository {
    private const val ROUTES_ASSET = "routes_2gis.json"

    @Volatile
    private var cachedRoutes: List<BusRouteMapItem>? = null

    fun loadRoutes(context: Context): List<BusRouteMapItem> {
        cachedRoutes?.let { return it }

        val parsed = context.assets.open(ROUTES_ASSET).bufferedReader().use { reader ->
            val root = Json.parseToJsonElement(reader.readText()).jsonArray
            root.mapNotNull { element ->
                val obj = element.jsonObject
                val number = obj["number"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val routeId = obj["route_id"]?.jsonPrimitive?.content ?: return@mapNotNull null

                val directions = obj["directions"]
                    ?.jsonArray
                    ?.mapNotNull { directionElement ->
                        val directionObj = directionElement.jsonObject
                        val pathPoints = directionObj["path_points"]
                            ?.jsonArray
                            ?.mapNotNull { pointElement ->
                                val coords = pointElement.jsonArray
                                val lon = coords.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull()
                                val lat = coords.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull()
                                if (lon == null || lat == null) {
                                    null
                                } else {
                                    RoutePathPoint(lon = lon, lat = lat)
                                }
                            }
                            .orEmpty()

                        if (pathPoints.size < 2) return@mapNotNull null

                        val platforms = directionObj["platforms"]
                            ?.jsonArray
                            ?.mapNotNull { platformElement ->
                                val platformObj = platformElement.jsonObject
                                val lon = platformObj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                                val lat = platformObj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null

                                RoutePlatformMapItem(
                                    platformId = platformObj["platform_id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                    stationId = platformObj["station_id"]?.jsonPrimitive?.content ?: "",
                                    name = platformObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                    lon = lon,
                                    lat = lat
                                )
                            }
                            .orEmpty()

                        RouteDirectionMapItem(
                            directionId = directionObj["direction_id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            directionType = directionObj["direction_type"]?.jsonPrimitive?.content ?: "",
                            pathPoints = pathPoints,
                            platforms = platforms
                        )
                    }
                    .orEmpty()

                if (directions.isEmpty()) return@mapNotNull null

                BusRouteMapItem(
                    id = routeId,
                    cityId = "almaty",
                    number = number,
                    fromName = obj["from_name"]?.jsonPrimitive?.content.orEmpty(),
                    toName = obj["to_name"]?.jsonPrimitive?.content.orEmpty(),
                    description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                    directions = directions
                )
            }
        }

        cachedRoutes = parsed
        return parsed
    }
}
