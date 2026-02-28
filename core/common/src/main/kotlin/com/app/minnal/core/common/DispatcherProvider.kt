package com.app.minnal.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides coroutine dispatchers for use throughout the application.
 *
 * This interface enables dependency injection of dispatchers, making it possible
 * to substitute test dispatchers during unit testing.
 */
interface DispatcherProvider {

    /** Dispatcher for UI/main thread operations. */
    val main: CoroutineDispatcher

    /** Dispatcher for I/O-bound operations such as disk or network access. */
    val io: CoroutineDispatcher

    /** Dispatcher for CPU-intensive computations. */
    val default: CoroutineDispatcher
}

/**
 * Default implementation of [DispatcherProvider] that delegates to the standard
 * [Dispatchers] provided by kotlinx.coroutines.
 */
class DefaultDispatcherProvider : DispatcherProvider {

    override val main: CoroutineDispatcher = Dispatchers.Main

    override val io: CoroutineDispatcher = Dispatchers.IO

    override val default: CoroutineDispatcher = Dispatchers.Default
}
