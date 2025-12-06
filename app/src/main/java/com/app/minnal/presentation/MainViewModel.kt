package com.app.minnal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.minnal.domain.model.BatteryInfo
import com.app.minnal.domain.usecase.GetBatteryInfoUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    getBatteryInfoUseCase: GetBatteryInfoUseCase
) : ViewModel() {

    val batteryInfo: StateFlow<BatteryInfo?> = getBatteryInfoUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}