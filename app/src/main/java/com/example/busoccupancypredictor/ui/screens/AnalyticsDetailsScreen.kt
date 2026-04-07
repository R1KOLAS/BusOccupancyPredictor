package com.example.busoccupancypredictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.data.BackendAnalyticsRepository
import com.example.busoccupancypredictor.domain.SeatStatus
import com.example.busoccupancypredictor.domain.StopFrequency
import com.example.busoccupancypredictor.ui.Brand
import com.example.busoccupancypredictor.ui.components.StatusPill
import com.example.busoccupancypredictor.ui.components.statusColor

@Composable
fun AnalyticsDetailsScreen(
    routeMapId: String,
    routeId: Int,
    routeLabel: String = routeId.toString(),
    useBackend: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fallbackAnalytics = remember(routeMapId) { buildAnalyticsBundle(context, routeMapId) }
    val cachedAnalytics = remember(routeMapId, useBackend) {
        if (useBackend) BackendAnalyticsRepository.getCachedAnalytics(routeMapId) else null
    }
    var backendFailed by remember(routeMapId, useBackend) { mutableStateOf(false) }
    var analytics by remember(routeMapId, useBackend) {
        mutableStateOf(
            when {
                cachedAnalytics != null -> buildAnalyticsBundle(context, routeMapId, cachedAnalytics)
                useBackend -> fallbackAnalytics.copy(
                    summary = emptyList(),
                    hours = emptyList(),
                    busyStops = emptyList(),
                    weekdayStats = emptyList(),
                    directionStats = emptyList()
                )
                else -> fallbackAnalytics
            }
        )
    }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(routeMapId, useBackend) {
        analytics = when {
            cachedAnalytics != null -> buildAnalyticsBundle(context, routeMapId, cachedAnalytics)
            useBackend -> fallbackAnalytics.copy(
                summary = emptyList(),
                hours = emptyList(),
                busyStops = emptyList(),
                weekdayStats = emptyList(),
                directionStats = emptyList()
            )
            else -> fallbackAnalytics
        }
        backendFailed = false
        if (useBackend) {
            runCatching { BackendAnalyticsRepository.fetchAnalytics(routeMapId) }
                .onSuccess { analytics = buildAnalyticsBundle(context, routeMapId, it) }
                .onFailure {
                    backendFailed = true
                    analytics = fallbackAnalytics.copy(
                        summary = emptyList(),
                        hours = emptyList(),
                        busyStops = emptyList(),
                        weekdayStats = emptyList(),
                        directionStats = emptyList()
                    )
                }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                Text("Аналитика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            AssistChip(
                onClick = { },
                label = { Text("Маршрут $routeLabel") },
                colors = AssistChipDefaults.assistChipColors(containerColor = Brand.GrayBg)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (backendFailed && useBackend) {
            SimpleBackendUnavailableBlock()
            Spacer(Modifier.height(10.dp))
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(Brand.GrayBg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabPill("Обзор", tab == 0) { tab = 0 }
            TabPill("Остановки", tab == 1) { tab = 1 }
            TabPill("Дни", tab == 2) { tab = 2 }
        }

        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> OverviewTabContent(analytics)
            1 -> StopsTabContent(analytics.busyStops)
            2 -> DaysTabContent(analytics.weekdayStats)
        }

        Spacer(Modifier.height(10.dp))

        AdviceBar(
            when (tab) {
                0 -> "Смотрите на направления и часы пик, чтобы выбирать более спокойные участки маршрута"
                1 -> "Выбирайте остановки с меньшей нагрузкой, чтобы ехать комфортнее"
                else -> "Для поездок без давки лучше подходят выходные и поздний вечер"
            }
        )

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RowScope.TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, color = Brand.Black)
    }
}

@Composable
private fun OverviewTabContent(analytics: AnalyticsBundle) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сводка маршрута", fontWeight = FontWeight.Bold)
                analytics.summary.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.title} (${item.timeRange})")
                        StatusPill(
                            status = item.status,
                            text = when (item.status) {
                                SeatStatus.AVAILABLE -> "Свободно"
                                SeatStatus.FEW -> "Средне"
                                SeatStatus.NONE -> "Плотно"
                            }
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Направления маршрута", fontWeight = FontWeight.Bold)
                analytics.directionStats.forEach { stat ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stat.title, modifier = Modifier.weight(1f))
                            Text("${stat.percent}%", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${stat.stopCount} остановок • ${stat.pathPointCount} точек траектории",
                            color = Brand.GrayText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Brand.GrayBorder)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(stat.percent / 100f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(statusColor(stat.status))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopsTabContent(list: List<StopFrequency>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Остановки с высокой загрузкой", fontWeight = FontWeight.Bold)
        Text("Где чаще всего меньше свободных мест", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Остановка", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
                    Text("Частота", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
                }

                list.forEach { row ->
                    StopFrequencyRow(row)
                }
            }
        }
    }
}

@Composable
private fun DaysTabContent(days: List<DayLoadStat>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Нагрузка по дням", fontWeight = FontWeight.Bold)
        Text("Как меняется спрос в течение недели", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                days.forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(day.label, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatusPill(
                                    status = day.status,
                                    text = when (day.status) {
                                        SeatStatus.AVAILABLE -> "Свободно"
                                        SeatStatus.FEW -> "Средне"
                                        SeatStatus.NONE -> "Плотно"
                                    }
                                )
                                Text("${day.percent}%", fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Brand.GrayBorder)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(day.percent / 100f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(statusColor(day.status))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopFrequencyRow(row: StopFrequency) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Brand.Black))
                Text(row.name, modifier = Modifier.widthIn(max = 220.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill(
                    status = row.status,
                    text = when (row.status) {
                        SeatStatus.AVAILABLE -> "Сидячие есть"
                        SeatStatus.FEW -> "Свободных мало"
                        SeatStatus.NONE -> "Сидячих нет"
                    }
                )
                Text("${row.percent}%", fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Brand.GrayBorder)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(row.percent / 100f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor(row.status))
            )
        }
    }
}

@Composable
private fun AdviceBar(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.Black)
            .padding(14.dp)
    ) {
        Text("Совет", color = Color.White, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun SimpleBackendUnavailableBlock() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сервер недоступен", fontWeight = FontWeight.Bold)
            Text(
                "В backend-режиме аналитика загружается только с сервера. Проверьте, что backend запущен.",
                color = Brand.GrayText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
