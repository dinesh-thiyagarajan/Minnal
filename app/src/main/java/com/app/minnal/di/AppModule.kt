package com.app.minnal.di

import com.app.minnal.core.common.DefaultDispatcherProvider
import com.app.minnal.core.common.DispatcherProvider
import com.app.minnal.core.data.engine.EngineConfig
import com.app.minnal.core.data.engine.TorrentEngine
import com.app.minnal.core.data.engine.TorrentEngineWrapper
import com.app.minnal.core.data.local.TorrentStorage
import com.app.minnal.core.data.repository.SettingsRepositoryImpl
import com.app.minnal.core.data.repository.TorrentRepositoryImpl
import com.app.minnal.core.domain.repository.SettingsRepository
import com.app.minnal.core.domain.repository.TorrentRepository
import com.app.minnal.core.domain.usecase.AddMagnetUseCase
import com.app.minnal.core.domain.usecase.AddTorrentUseCase
import com.app.minnal.core.domain.usecase.GetSettingsUseCase
import com.app.minnal.core.domain.usecase.GetTorrentDetailUseCase
import com.app.minnal.core.domain.usecase.GetTorrentsUseCase
import com.app.minnal.core.domain.usecase.PauseTorrentUseCase
import com.app.minnal.core.domain.usecase.RemoveTorrentUseCase
import com.app.minnal.core.domain.usecase.ResumeTorrentUseCase
import com.app.minnal.core.domain.usecase.UpdateSettingsUseCase
import com.app.minnal.feature.addtorrent.AddTorrentViewModel
import com.app.minnal.feature.settings.SettingsViewModel
import com.app.minnal.feature.torrentlist.TorrentListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin dependency injection module defining all application dependencies.
 *
 * Configures the engine, storage, repositories, use cases, and ViewModels
 * for the entire Minnal torrent client application.
 */
val appModule = module {

    // Dispatcher provider
    single<DispatcherProvider> { DefaultDispatcherProvider() }

    // Engine
    single { EngineConfig() }
    single { TorrentEngine(get()) }
    single { TorrentEngineWrapper(get(), get()) }

    // Storage
    single { TorrentStorage(androidContext()) }

    // Repositories
    single<TorrentRepository> { TorrentRepositoryImpl(get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }

    // Use Cases
    factory { GetTorrentsUseCase(get()) }
    factory { GetTorrentDetailUseCase(get()) }
    factory { AddTorrentUseCase(get()) }
    factory { AddMagnetUseCase(get()) }
    factory { PauseTorrentUseCase(get()) }
    factory { ResumeTorrentUseCase(get()) }
    factory { RemoveTorrentUseCase(get()) }
    factory { GetSettingsUseCase(get()) }
    factory { UpdateSettingsUseCase(get()) }

    // ViewModels
    viewModel { TorrentListViewModel(get(), get(), get(), get()) }
    viewModel { AddTorrentViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
