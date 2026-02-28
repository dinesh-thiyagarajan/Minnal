package com.app.minnal.core.common

/**
 * A sealed class representing the result of an operation that can either succeed with data,
 * fail with an error message, or be in a loading state.
 *
 * @param T the type of data held in a successful result
 */
sealed class MinnalResult<out T> {

    /**
     * Represents a successful operation containing the resulting [data].
     *
     * @param T the type of the data
     * @property data the successful result value
     */
    data class Success<T>(val data: T) : MinnalResult<T>()

    /**
     * Represents a failed operation with an error [message] and an optional [throwable] cause.
     *
     * @property message a human-readable description of the error
     * @property throwable the optional underlying exception that caused the error
     */
    data class Error(val message: String, val throwable: Throwable? = null) : MinnalResult<Nothing>()

    /**
     * Represents an operation that is currently in progress.
     */
    data object Loading : MinnalResult<Nothing>()
}

/**
 * Returns the data if this is a [MinnalResult.Success], or null otherwise.
 *
 * @return the success data, or null if this result is not a success
 */
fun <T> MinnalResult<T>.getOrNull(): T? = when (this) {
    is MinnalResult.Success -> data
    is MinnalResult.Error -> null
    is MinnalResult.Loading -> null
}

/**
 * Returns the data if this is a [MinnalResult.Success], or throws an exception otherwise.
 *
 * @return the success data
 * @throws IllegalStateException if this result is [MinnalResult.Loading]
 * @throws RuntimeException wrapping the original throwable if this result is [MinnalResult.Error]
 */
fun <T> MinnalResult<T>.getOrThrow(): T = when (this) {
    is MinnalResult.Success -> data
    is MinnalResult.Error -> throw throwable ?: RuntimeException(message)
    is MinnalResult.Loading -> throw IllegalStateException("Result is still loading")
}

/**
 * Transforms the data of a successful result using the given [transform] function.
 * If this result is an [MinnalResult.Error] or [MinnalResult.Loading], it is returned unchanged.
 *
 * @param R the type of the transformed data
 * @param transform the function to apply to the success data
 * @return a new [MinnalResult] with the transformed data, or the original error/loading state
 */
fun <T, R> MinnalResult<T>.map(transform: (T) -> R): MinnalResult<R> = when (this) {
    is MinnalResult.Success -> MinnalResult.Success(transform(data))
    is MinnalResult.Error -> this
    is MinnalResult.Loading -> this
}
