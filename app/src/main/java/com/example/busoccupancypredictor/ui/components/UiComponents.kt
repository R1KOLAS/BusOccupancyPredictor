package com.example.busoccupancypredictor.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.domain.SeatStatus
import com.example.busoccupancypredictor.ui.Brand

sealed class BottomTab(val route: String, val label: String, val icon: @Composable () -> Unit) {
    data object Routes : BottomTab("routes", "Маршруты", { Icon(Icons.Outlined.DirectionsBus, null) })
    data object Map : BottomTab("map", "Карта", { Icon(Icons.Outlined.Map, null) })
    data object Forecast : BottomTab("forecast", "Прогноз", { Icon(Icons.Outlined.ShowChart, null) })
    data object Analytics : BottomTab("analytics", "Аналитика", { Icon(Icons.Outlined.BarChart, null) })
}

@Composable
fun AppBottomBar(
    selectedRoute: String,
    showRouteInsights: Boolean,
    onSelect: (BottomTab) -> Unit
) {
    val tabs = buildList {
        add(BottomTab.Routes)
        add(BottomTab.Map)
        if (showRouteInsights) {
            add(BottomTab.Forecast)
            add(BottomTab.Analytics)
        }
    }

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.animateContentSize()
    ) {
        tabs.forEach { t ->
            val selected = selectedRoute == t.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(t) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (selected) Brand.Yellow else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { t.icon() }
                },
                label = { Text(t.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Brand.Black,
                    unselectedIconColor = Brand.Black,
                    selectedTextColor = Brand.Black,
                    unselectedTextColor = Brand.GrayText,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun StatusPill(status: SeatStatus, text: String, modifier: Modifier = Modifier) {
    val dot = statusColor(status)
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Brand.GrayBorder, RoundedCornerShape(999.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun YellowOutlineCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Brand.Yellow, RoundedCornerShape(16.dp))
            .background(Brand.YellowSoft)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun Segmented(
    left: String,
    right: String,
    selectedLeft: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brand.GrayBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SegItem(left, selectedLeft, onSelectLeft)
        SegItem(right, !selectedLeft, onSelectRight)
    }
}

@Composable
private fun RowScope.SegItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Brand.Yellow else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, color = Brand.Black)
    }
}

fun statusColor(s: SeatStatus): Color = when (s) {
    SeatStatus.AVAILABLE -> Brand.Green
    SeatStatus.FEW -> Brand.Orange
    SeatStatus.NONE -> Brand.Red
}
