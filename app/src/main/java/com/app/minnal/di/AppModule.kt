package com.app.minnal.di

import com.app.minnal.domain.usecase.GetBatteryInfoUseCase
import com.app.minnal.presentation.MainViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { GetBatteryInfoUseCase(androidContext()) }
    viewModel { MainViewModel(get()) }
}
