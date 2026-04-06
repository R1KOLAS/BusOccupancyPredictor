package com.example.busoccupancypredictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.ui.Brand

@Composable
fun IntroScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        TopWave()

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(140.dp))
            BusLogo()
            Spacer(Modifier.height(10.dp))

            Text("QalaBus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "Прогноз загруженности транспорта",
                color = Brand.GrayText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            FeatureCard()

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Начать", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(14.dp))
            Text("Прототип • Версия 1.0", color = Brand.GrayText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeatureCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Brand.GrayBorder, RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FeatureRow("Выбери маршрут и остановку")
        FeatureRow("Прогноз учитывает погодные условия")
        FeatureRow("Прогноз 10 / 20 / 30 / 60 минут")
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Brand.Yellow),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Brand.Black)
            )
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BusLogo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 18.dp, height = 4.dp).background(Brand.Black, RoundedCornerShape(99.dp)))
                    Box(Modifier.size(width = 26.dp, height = 4.dp).background(Brand.Black, RoundedCornerShape(99.dp)))
                    Box(Modifier.size(width = 18.dp, height = 4.dp).background(Brand.Black, RoundedCornerShape(99.dp)))
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(width = 72.dp, height = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brand.Black)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            Modifier
                                .size(width = 34.dp, height = 14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brand.Yellow)
                        )
                        Box(
                            Modifier
                                .size(width = 14.dp, height = 14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brand.Yellow)
                        )
                    }
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Yellow))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Yellow))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopWave() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Brand.Yellow)
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.72f)
            quadraticBezierTo(w * 0.25f, h * 0.80f, w * 0.5f, h * 0.72f)
            quadraticBezierTo(w * 0.75f, h * 0.64f, w, h * 0.72f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, color = androidx.compose.ui.graphics.Color(0xFFFFC700), style = Fill)
    }
}
