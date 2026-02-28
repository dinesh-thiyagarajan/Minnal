package com.app.minnal.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.app.minnal.core.domain.model.TorrentStatus
import com.app.minnal.core.ui.theme.StatusDownloading
import com.app.minnal.core.ui.theme.StatusError
import com.app.minnal.core.ui.theme.StatusPaused
import com.app.minnal.core.ui.theme.StatusSeeding

/**
 * A themed progress bar for displaying torrent download progress.
 *
 * The progress bar color changes based on the torrent's status:
 * - Green for downloading
 * - Blue for seeding
 * - Yellow for paused
 * - Red for error
 *
 * @param progress the current progress value between 0.0 and 1.0
 * @param status the current torrent status for color determination
 * @param modifier optional modifier for the composable
 */
@Composable
fun TorrentProgressBar(
    progress: Float,
    status: TorrentStatus,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "progressAnimation"
    )

    val progressColor = when (status) {
        TorrentStatus.DOWNLOADING -> StatusDownloading
        TorrentStatus.SEEDING -> StatusSeeding
        TorrentStatus.PAUSED -> StatusPaused
        TorrentStatus.ERROR -> StatusError
        TorrentStatus.CHECKING -> MaterialTheme.colorScheme.tertiary
        TorrentStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        TorrentStatus.STOPPED -> MaterialTheme.colorScheme.outline
    }

    val trackColor = progressColor.copy(alpha = 0.2f)

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = progressColor,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
}
