package com.app.minnal.domain.usecase

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.app.minnal.domain.model.BatteryInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GetBatteryInfoUseCase(private val context: Context) {

    operator fun invoke(): Flow<BatteryInfo?> = callbackFlow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.div(1000.0)
                val chargingSpeed = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

                if (voltage != null && voltage > 0) {
                    trySend(BatteryInfo(voltage = voltage, chargingSpeed = chargingSpeed))
                } else {
                    trySend(null)
                }
            }
        }

        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, intentFilter)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}