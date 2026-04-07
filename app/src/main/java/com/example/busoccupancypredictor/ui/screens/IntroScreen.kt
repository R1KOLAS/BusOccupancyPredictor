package com.example.busoccupancypredictor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.busoccupancypredictor.R
import com.example.busoccupancypredictor.ui.Brand

@Composable
fun IntroScreen(
    onStartBackend: () -> Unit,
    onStartLocal: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        TopWave()

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(90.dp))
            BusLogo()
            Spacer(Modifier.height(0.dp))

            Text("QalaBus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))
            Text(
                "Прогноз загруженности транспорта",
                color = Brand.GrayText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            FeatureCard()

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = onStartBackend,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Начать", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onStartLocal,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Запуск локально", color = Brand.Black, fontWeight = FontWeight.SemiBold)
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
            .background(Color.White)
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
    Image(
        painter = painterResource(R.drawable.ic_map),
        contentDescription = "QalaBus",
        modifier = Modifier.size(132.dp)
    )
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
        drawPath(path, color = Color(0xFFFFC700), style = Fill)
    }
}
