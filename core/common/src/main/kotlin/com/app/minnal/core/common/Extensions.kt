package com.app.minnal.core.common

import java.util.Locale

/**
 * Converts a byte count to a human-readable size string.
 *
 * Examples:
 * - 1536L -> "1.5 KB"
 * - 1073741824L -> "1.0 GB"
 * - 500L -> "500 B"
 *
 * @return a formatted string representing the size in the most appropriate unit
 */
fun Long.toHumanReadableSize(): String {
    if (this < 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unitIndex = 0

    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        "${this} B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

/**
 * Converts a bytes-per-second value to a human-readable speed string.
 *
 * Examples:
 * - 1572864L -> "1.5 MB/s"
 * - 262144L -> "256.0 KB/s"
 * - 500L -> "500 B/s"
 *
 * @return a formatted string representing the speed in the most appropriate unit
 */
fun Long.toHumanReadableSpeed(): String {
    if (this < 0) return "0 B/s"

    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = this.toDouble()
    var unitIndex = 0

    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        "${this} B/s"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

/**
 * Converts a duration in seconds to a human-readable time string.
 *
 * Examples:
 * - 8100L -> "2h 15m"
 * - 330L -> "5m 30s"
 * - 45L -> "< 1m"
 * - -1L -> "Unknown"
 *
 * @return a formatted time string
 */
fun Long.toTimeString(): String {
    if (this < 0) return "Unknown"
    if (this < 60) return "< 1m"

    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "< 1m"
    }
}

/**
 * Converts a float value (0.0 to 1.0) to a percentage string.
 *
 * Examples:
 * - 0.756f -> "75.6%"
 * - 1.0f -> "100.0%"
 * - 0.0f -> "0.0%"
 *
 * @return a formatted percentage string
 */
fun Float.toPercentString(): String {
    val percentage = (this * 100).coerceIn(0f, 100f)
    return String.format(Locale.US, "%.1f%%", percentage)
}
