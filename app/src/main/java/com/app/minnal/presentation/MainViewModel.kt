package com.app.minnal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.minnal.domain.model.BatteryInfo
import com.app.minnal.domain.usecase.GetBatteryInfoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val getBatteryInfoUseCase: GetBatteryInfoUseCase
) : ViewModel() {

    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo = _batteryInfo.asStateFlow()

    fun getBatteryInfo() {
        viewModelScope.launch {
            _batteryInfo.value = getBatteryInfoUseCase()
        }
    }
}