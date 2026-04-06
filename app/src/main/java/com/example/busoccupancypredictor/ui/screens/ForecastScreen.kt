package com.example.busoccupancypredictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.data.FakeData
import com.example.busoccupancypredictor.data.RoutesRepository
import com.example.busoccupancypredictor.domain.ForecastPoint
import com.example.busoccupancypredictor.domain.LoadNow
import com.example.busoccupancypredictor.domain.RoutePlatformMapItem
import com.example.busoccupancypredictor.domain.SeatStatus
import com.example.busoccupancypredictor.ui.Brand
import com.example.busoccupancypredictor.ui.components.StatusPill
import com.example.busoccupancypredictor.ui.components.YellowOutlineCard
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ForecastScreen(
    routeMapId: String,
    routeId: Int,
    routeLabel: String = routeId.toString(),
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val routes = remember {
        runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    }
    val route = remember(routeMapId, routes) {
        routes.firstOrNull { it.id == routeMapId }
    }
    val stopOptions = remember(route) {
        route
            ?.directions
            ?.flatMap { direction ->
                direction.platforms.mapIndexed { index, platform ->
                    ForecastStopOption(
                        key = "${direction.directionId}:${platform.platformId}",
                        name = platform.name,
                        directionLabel = directionLabel(direction.directionType, route.fromName, route.toName),
                        stopIndex = index,
                        totalStops = direction.platforms.size,
                        platform = platform
                    )
                }
            }
            .orEmpty()
    }
    var selectedStopKey by rememberSaveable(routeMapId) {
        mutableStateOf(stopOptions.firstOrNull()?.key.orEmpty())
    }
    val selectedStop = remember(selectedStopKey, stopOptions) {
        stopOptions.firstOrNull { it.key == selectedStopKey } ?: stopOptions.firstOrNull()
    }

    val dayOptions = remember { listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") }
    val timeOptions = remember { buildTimeOptions() }
    val currentDay = remember { currentDayLabel() }
    val currentTime = remember { nearestTimeLabel(timeOptions) }

    var selectedDay by rememberSaveable(routeMapId) { mutableStateOf(currentDay) }
    var selectedTime by rememberSaveable(routeMapId) { mutableStateOf(currentTime) }
    var selectedWeather by rememberSaveable(routeMapId) { mutableStateOf<WeatherOption?>(null) }

    val currentLoad = remember(routeId, selectedStop?.key, selectedDay, selectedTime, selectedWeather) {
        adjustedCurrentLoad(routeId, selectedStop, selectedDay, selectedTime, selectedWeather)
    }
    val forecast = remember(routeId, selectedStop?.key, selectedDay, selectedTime, selectedWeather) {
        adjustedForecast(routeId, selectedStop, selectedDay, selectedTime, selectedWeather)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 92.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                        }
                    }
                    Text("Прогноз", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                AssistChip(
                    onClick = { },
                    label = { Text("Маршрут $routeLabel") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Brand.GrayBg)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text("Остановка", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            StopDropdown(
                options = stopOptions,
                selected = selectedStop,
                onSelect = { selectedStopKey = it.key }
            )

            Spacer(Modifier.height(12.dp))

            Text("Параметры прогноза", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ParameterChip(
                    main = selectedDay,
                    sub = "День",
                    options = dayOptions,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                    onSelect = { selectedDay = it }
                )
                ParameterChip(
                    main = selectedTime,
                    sub = "Время",
                    options = timeOptions,
                    outlined = false,
                    modifier = Modifier.weight(1f),
                    onSelect = { selectedTime = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherChip(
                    label = "Дождь",
                    selected = selectedWeather == WeatherOption.RAIN,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedWeather = if (selectedWeather == WeatherOption.RAIN) null else WeatherOption.RAIN
                    }
                )
                WeatherChip(
                    label = "Снег",
                    selected = selectedWeather == WeatherOption.SNOW,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedWeather = if (selectedWeather == WeatherOption.SNOW) null else WeatherOption.SNOW
                    }
                )
            }

            selectedStop?.let {
                Spacer(Modifier.height(8.dp))
                DirectionInfoCard(
                    direction = it.directionLabel,
                    stopText = "${it.name} • ${it.stopIndex + 1} из ${it.totalStops}"
                )
            }

            Spacer(Modifier.height(12.dp))

            YellowOutlineCard {
                Text("Сейчас", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                StatusPill(
                    status = currentLoad.status,
                    text = when (currentLoad.status) {
                        SeatStatus.AVAILABLE -> "Сидячие есть"
                        SeatStatus.FEW -> "Свободных мало"
                        SeatStatus.NONE -> "Сидячих нет"
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (currentLoad.status) {
                        SeatStatus.AVAILABLE -> "Есть сидячие места"
                        SeatStatus.FEW -> "Свободных сидячих мест мало"
                        SeatStatus.NONE -> "Свободных сидячих мест нет"
                    },
                    color = Brand.GrayText,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("${currentLoad.percent}%", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Прогноз на ближайшее время", fontWeight = FontWeight.Bold)
            Text("10 / 20 / 30 / 60 минут", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                forecast.chunked(2).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { point ->
                            Box(Modifier.weight(1f)) {
                                ForecastCard(point)
                            }
                        }
                        repeat(2 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        RecommendationBar(
            text = recommendationText(selectedStop, selectedDay, selectedTime, forecast),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private data class ForecastStopOption(
    val key: String,
    val name: String,
    val directionLabel: String,
    val stopIndex: Int,
    val totalStops: Int,
    val platform: RoutePlatformMapItem
)

private enum class WeatherOption {
    RAIN,
    SNOW
}

@Composable
private fun StopDropdown(
    options: List<ForecastStopOption>,
    selected: ForecastStopOption?,
    onSelect: (ForecastStopOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val value = selected?.name ?: "Остановки не найдены"

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Brand.GrayBorder, RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(value, fontWeight = FontWeight.SemiBold)
                selected?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${it.directionLabel} • ${it.stopIndex + 1} из ${it.totalStops}",
                        color = Brand.GrayText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = { if (options.isNotEmpty()) expanded = true }) {
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Brand.GrayText)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${option.directionLabel} • ${option.stopIndex + 1} из ${option.totalStops}",
                                color = Brand.GrayText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ParameterChip(
    main: String,
    sub: String,
    options: List<String>,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        ChipLike(
            main = main,
            sub = sub,
            outlined = outlined,
            clickable = true,
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DirectionInfoCard(direction: String, stopText: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Brand.GrayBorder, RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text("Направление: $direction", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            stopText,
            color = Brand.GrayText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun adjustedCurrentLoad(
    routeId: Int,
    stop: ForecastStopOption?,
    dayLabel: String,
    timeLabel: String,
    weather: WeatherOption?
): LoadNow {
    val base = FakeData.loadNow(routeId)
    val adjustedPercent = (
        base.percent +
            occupancyShift(stop) +
            dayShift(dayLabel) +
            timeShift(timeLabel) +
            weatherShift(weather)
        ).coerceIn(5, 95)
    return LoadNow(
        status = seatStatusForPercent(adjustedPercent),
        percent = adjustedPercent
    )
}

private fun adjustedForecast(
    routeId: Int,
    stop: ForecastStopOption?,
    dayLabel: String,
    timeLabel: String,
    weather: WeatherOption?
): List<ForecastPoint> {
    val delta = occupancyShift(stop) + dayShift(dayLabel) + timeShift(timeLabel) + weatherShift(weather)
    return FakeData.forecast(routeId).map { point ->
        val adjustedPercent = (point.percent + delta).coerceIn(5, 95)
        ForecastPoint(
            minutes = point.minutes,
            status = seatStatusForPercent(adjustedPercent),
            percent = adjustedPercent
        )
    }
}

private fun occupancyShift(stop: ForecastStopOption?): Int {
    if (stop == null || stop.totalStops <= 1) return 0
    val progress = stop.stopIndex.toDouble() / (stop.totalStops - 1).coerceAtLeast(1)
    return ((progress - 0.5) * 24.0).roundToInt()
}

private fun dayShift(dayLabel: String): Int = when (dayLabel) {
    "Пн", "Вт", "Ср", "Чт" -> 4
    "Пт" -> 8
    "Сб" -> -6
    "Вс" -> -10
    else -> 0
}

private fun timeShift(timeLabel: String): Int {
    val hour = timeLabel.substringBefore(":").toIntOrNull() ?: return 0
    return when (hour) {
        in 7..9 -> 14
        in 12..14 -> 6
        in 17..19 -> 16
        in 20..22 -> -4
        else -> -8
    }
}

private fun weatherShift(weather: WeatherOption?): Int = when (weather) {
    WeatherOption.RAIN -> 8
    WeatherOption.SNOW -> 12
    null -> 0
}

private fun seatStatusForPercent(percent: Int): SeatStatus = when {
    percent >= 70 -> SeatStatus.NONE
    percent >= 45 -> SeatStatus.FEW
    else -> SeatStatus.AVAILABLE
}

private fun directionLabel(directionType: String, fromName: String, toName: String): String {
    return when (directionType.lowercase()) {
        "forward" -> if (toName.isNotBlank()) "В сторону $toName" else "Прямое направление"
        "backward" -> if (fromName.isNotBlank()) "В сторону $fromName" else "Обратное направление"
        else -> listOf(fromName, toName).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Маршрут" }
    }
}

private fun currentDayLabel(): String {
    return when (LocalDate.now().dayOfWeek.value) {
        1 -> "Пн"
        2 -> "Вт"
        3 -> "Ср"
        4 -> "Чт"
        5 -> "Пт"
        6 -> "Сб"
        else -> "Вс"
    }
}

private fun buildTimeOptions(): List<String> {
    val options = mutableListOf<String>()
    for (hour in 6..23) {
        listOf(0, 15, 30, 45).forEach { minute ->
            options += "%02d:%02d".format(hour, minute)
        }
    }
    return options
}

private fun nearestTimeLabel(options: List<String>): String {
    val now = LocalTime.now()
    val targetMinutes = now.hour * 60 + now.minute
    return options.minByOrNull { option ->
        val hour = option.substringBefore(":").toIntOrNull() ?: 0
        val minute = option.substringAfter(":").toIntOrNull() ?: 0
        abs((hour * 60 + minute) - targetMinutes)
    } ?: options.first()
}

private fun recommendationText(
    stop: ForecastStopOption?,
    dayLabel: String,
    timeLabel: String,
    forecast: List<ForecastPoint>
): String {
    val bestPoint = forecast.minByOrNull { it.percent }
    return if ((bestPoint?.minutes ?: 0) <= 20) {
        "Рекомендация: для ${stop?.name ?: "маршрута"} в $dayLabel около $timeLabel загрузка скоро снизится, можно подождать 10–20 минут"
    } else {
        "Рекомендация: для ${stop?.name ?: "маршрута"} в $dayLabel около $timeLabel лучше планировать посадку без ожидания"
    }
}

@Composable
private fun ChipLike(
    main: String,
    sub: String?,
    outlined: Boolean,
    clickable: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (outlined) Brand.Yellow else Brand.GrayBorder, RoundedCornerShape(999.dp))
            .background(Color.White)
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(main, fontWeight = FontWeight.Bold)
        if (sub != null) {
            Text(sub, color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
        }
        if (clickable) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = Brand.GrayText)
        }
    }
}

@Composable
private fun WeatherChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                1.dp,
                if (selected) Brand.Yellow else Brand.GrayBorder,
                RoundedCornerShape(999.dp)
            )
            .background(if (selected) Brand.YellowSoft else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Brand.Black)
    }
}

@Composable
private fun ForecastCard(f: ForecastPoint) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Через ${f.minutes} мин", fontWeight = FontWeight.Bold)
            StatusPill(
                status = f.status,
                text = when (f.status) {
                    SeatStatus.AVAILABLE -> "Сидячие есть"
                    SeatStatus.FEW -> "Свободных мало"
                    SeatStatus.NONE -> "Сидячих нет"
                }
            )
            Text(
                when (f.status) {
                    SeatStatus.AVAILABLE -> "Есть сидячие места"
                    SeatStatus.FEW -> "Сидячих мест мало"
                    SeatStatus.NONE -> "Сидячих мест нет"
                } + "  ${f.percent}%",
                color = Brand.GrayText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RecommendationBar(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.Black)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
