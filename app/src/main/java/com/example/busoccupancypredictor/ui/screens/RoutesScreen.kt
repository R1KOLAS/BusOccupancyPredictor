package com.example.busoccupancypredictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.data.RoutesRepository
import com.example.busoccupancypredictor.domain.BusRouteMapItem
import com.example.busoccupancypredictor.ui.Brand

@Composable
fun RoutesScreen(onRouteClick: (String) -> Unit) {
    val context = LocalContext.current
    val allRoutes = remember {
        runCatching { RoutesRepository.loadRoutes(context) }.getOrDefault(emptyList())
    }
    val routes = remember(allRoutes) {
        allRoutes
            .filter { it.cityId == "almaty" }
            .sortedWith(compareBy<BusRouteMapItem>({ routeSortGroup(it.number) }, { routeSortSuffix(it.number) }))
    }

    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredRoutes = remember(routes, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            routes
        } else {
            val needle = normalizedQuery.lowercase()
            routes.filter { route ->
                route.number.lowercase().contains(needle) ||
                    route.fromName.lowercase().contains(needle) ||
                    route.toName.lowercase().contains(needle) ||
                    route.description.lowercase().contains(needle)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Brand.Black)
            Text("Алматы", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(18.dp))

        SearchBar(
            value = query,
            onValueChange = { query = it.take(40) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Text(
            "Маршрутов: ${filteredRoutes.size}",
            color = Brand.GrayText,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredRoutes, key = { it.id }) { route ->
                RouteRow(
                    number = route.number,
                    subtitle = routeSubtitle(route),
                    onClick = { onRouteClick(route.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = false,
        onExpandedChange = {},
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("Введите номер маршрута или направление") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Brand.GrayText) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
    }
}

@Composable
private fun RouteRow(number: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brand.GrayBg),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brand.Black)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(number, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun routeSubtitle(route: BusRouteMapItem): String {
    val fromTo = listOf(route.fromName, route.toName)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    return if (fromTo.isNotBlank()) fromTo else "Маршрут без описания"
}

private fun routeSortGroup(value: String): Int {
    return value.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
}

private fun routeSortSuffix(value: String): String {
    return value.dropWhile { it.isDigit() }
}
