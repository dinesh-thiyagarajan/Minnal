package com.app.minnal.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.app.minnal.domain.model.BatteryInfo

class GetBatteryInfoUseCase(private val context: Context) {

    operator fun invoke(): BatteryInfo? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
        
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.div(1000.0)
        val chargingSpeed = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        return if (voltage != null && voltage > 0) {
            BatteryInfo(voltage = voltage, chargingSpeed = chargingSpeed)
        } else {
            null
        }
    }
}