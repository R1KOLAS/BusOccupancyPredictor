package com.example.busoccupancypredictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.domain.HourLoad
import com.example.busoccupancypredictor.domain.SeatStatus
import com.example.busoccupancypredictor.domain.TimeSummary
import com.example.busoccupancypredictor.ui.Brand
import com.example.busoccupancypredictor.ui.components.statusColor

@Composable
fun AnalyticsScreen(
    routeMapId: String,
    routeId: Int,
    routeLabel: String = routeId.toString(),
    onOpenDetails: () -> Unit
) {
    val context = LocalContext.current
    val analytics = remember(routeMapId) {
        buildAnalyticsBundle(context, routeMapId)
    }
    val summary = analytics.summary
    val hours = analytics.hours
    val topStops = analytics.busyStops.take(3)

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Аналитика", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AssistChip(
                onClick = { },
                label = { Text("Маршрут $routeLabel") },
                colors = AssistChipDefaults.assistChipColors(containerColor = Brand.GrayBg)
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDetails() }
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сводка по времени", fontWeight = FontWeight.Bold)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    summary.forEach { item ->
                        SummaryChip(item, Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Загрузка по часам", fontWeight = FontWeight.Bold)
                Text("Цвет = состояние мест", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
                HourBars(hours)
                LegendRow()
            }
        }

        if (topStops.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Самые загруженные остановки", fontWeight = FontWeight.Bold)
                    topStops.forEach { stop ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stop.name, modifier = Modifier.weight(1f))
                            Text("${stop.percent}%", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SummaryChip(s: TimeSummary, modifier: Modifier = Modifier) {
    val border = if (s.highlighted) Brand.Yellow else Brand.GrayBorder
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(s.status)))
        }
        Text(s.timeRange, color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        Text(
            when (s.status) {
                SeatStatus.AVAILABLE -> "Сидячие есть"
                SeatStatus.FEW -> "Свободных мало"
                SeatStatus.NONE -> "Полная\nзагрузка"
            },
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )
    }
}

@Composable
private fun HourBars(items: List<HourLoad>) {
    val count = items.size.coerceAtLeast(1)
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brand.GrayBg)
            .padding(12.dp)
    ) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxSize()
                .padding(bottom = 18.dp)
        ) {
            val w = size.width
            val h = size.height
            val barW = w / (items.size.coerceAtLeast(1) * 1.6f)
            val gap = barW * 0.6f
            val maxH = h * 0.85f

            items.forEachIndexed { i, item ->
                val x = i * (barW + gap) + gap
                val height = when (item.status) {
                    SeatStatus.NONE -> maxH
                    SeatStatus.FEW -> maxH * 0.75f
                    SeatStatus.AVAILABLE -> maxH * 0.45f
                }
                val top = h - height
                drawRoundRect(
                    color = statusColor(item.status),
                    topLeft = androidx.compose.ui.geometry.Offset(x, top),
                    size = androidx.compose.ui.geometry.Size(barW, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${item.hour}:00",
                        color = Brand.GrayText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            repeat(count - items.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(Brand.Green, "Сидячие есть")
        LegendDot(Brand.Orange, "Свободных мало")
        LegendDot(Brand.Red, "Сидячих нет")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
    }
}
