package com.app.minnal.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.app.minnal.core.domain.model.TorrentStatus
import com.app.minnal.core.ui.theme.StatusChecking
import com.app.minnal.core.ui.theme.StatusDownloading
import com.app.minnal.core.ui.theme.StatusError
import com.app.minnal.core.ui.theme.StatusPaused
import com.app.minnal.core.ui.theme.StatusQueued
import com.app.minnal.core.ui.theme.StatusSeeding
import com.app.minnal.core.ui.theme.StatusStopped

/**
 * Returns the appropriate icon for the given torrent status.
 *
 * @param status the current torrent status
 * @return the corresponding Material Design icon vector
 */
fun statusIcon(status: TorrentStatus): ImageVector {
    return when (status) {
        TorrentStatus.DOWNLOADING -> Icons.Filled.CloudDownload
        TorrentStatus.SEEDING -> Icons.Filled.CloudUpload
        TorrentStatus.PAUSED -> Icons.Filled.Pause
        TorrentStatus.STOPPED -> Icons.Filled.Stop
        TorrentStatus.CHECKING -> Icons.Filled.CheckCircle
        TorrentStatus.QUEUED -> Icons.Filled.HourglassTop
        TorrentStatus.ERROR -> Icons.Filled.Error
    }
}

/**
 * Returns the appropriate color for the given torrent status.
 *
 * @param status the current torrent status
 * @return the corresponding status color
 */
fun statusColor(status: TorrentStatus): Color {
    return when (status) {
        TorrentStatus.DOWNLOADING -> StatusDownloading
        TorrentStatus.SEEDING -> StatusSeeding
        TorrentStatus.PAUSED -> StatusPaused
        TorrentStatus.STOPPED -> StatusStopped
        TorrentStatus.CHECKING -> StatusChecking
        TorrentStatus.QUEUED -> StatusQueued
        TorrentStatus.ERROR -> StatusError
    }
}

/**
 * Returns a human-readable display string for the given torrent status.
 *
 * @param status the current torrent status
 * @return the display name of the status
 */
fun statusText(status: TorrentStatus): String {
    return when (status) {
        TorrentStatus.DOWNLOADING -> "Downloading"
        TorrentStatus.SEEDING -> "Seeding"
        TorrentStatus.PAUSED -> "Paused"
        TorrentStatus.STOPPED -> "Stopped"
        TorrentStatus.CHECKING -> "Checking"
        TorrentStatus.QUEUED -> "Queued"
        TorrentStatus.ERROR -> "Error"
    }
}
