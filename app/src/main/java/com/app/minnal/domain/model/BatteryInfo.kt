package com.app.minnal.domain.model

data class BatteryInfo(
    val voltage: Double,
    val chargingSpeed: Long,
    val isCharging: Boolean
)