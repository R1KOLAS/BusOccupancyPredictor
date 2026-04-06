package com.example.busoccupancypredictor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.busoccupancypredictor.ui.AppRoot
import com.example.busoccupancypredictor.ui.theme.BusOccupancyPredictorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BusOccupancyPredictorTheme {
                AppRoot()
            }
        }
    }
}
