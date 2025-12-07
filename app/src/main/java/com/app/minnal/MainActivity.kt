package com.app.minnal

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.minnal.domain.model.BatteryInfo
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

                Scaffold(content = { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BatteryInfoDisplay(batteryInfo = batteryInfo)
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun BatteryInfoDisplay(batteryInfo: BatteryInfo?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Crossfade(targetState = batteryInfo, label = "batteryInfoCrossfade") {
            when (it) {
                null -> {
                    Text(text = "Fetching battery info...", style = MaterialTheme.typography.headlineMedium)
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = it.voltage,
                            transitionSpec = {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                        slideOutVertically { height -> -height } + fadeOut()
                            },
                            label = "voltageAnimation"
                        ) { targetVoltage ->
                            Text(
                                text = "Voltage: ${String.format("%.2f", targetVoltage)}V",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        AnimatedContent(
                            targetState = it.chargingSpeed,
                            transitionSpec = {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                        slideOutVertically { height -> -height } + fadeOut()
                            },
                            label = "chargingSpeedAnimation"
                        ) { targetChargingSpeed ->
                            Text(
                                text = "Charging Speed: $targetChargingSpeed µA",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BatteryInfoDisplayPreview() {
    MinnalTheme {
        BatteryInfoDisplay(batteryInfo = BatteryInfo(voltage = 4.2, chargingSpeed = 150000))
    }
}
