package com.app.minnal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.app.minnal.presentation.MainViewModel
import com.app.minnal.ui.theme.MinnalTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinnalTheme {
                val viewModel: MainViewModel by viewModel()
                val batteryInfo by viewModel.batteryInfo.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.getBatteryInfo()
                }

                Scaffold(content = { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            batteryInfo?.let { info ->
                                Text(text = "Voltage: ${info.voltage}V", style = MaterialTheme.typography.headlineMedium)
                                Text(text = "Charging Speed: ${info.chargingSpeed} µA", style = MaterialTheme.typography.headlineMedium)
                            } ?: Text(text = "Fetching battery info...", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
