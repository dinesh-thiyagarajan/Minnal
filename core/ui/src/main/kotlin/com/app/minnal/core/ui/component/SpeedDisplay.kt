package com.app.minnal.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.app.minnal.core.common.toHumanReadableSpeed
import com.app.minnal.core.ui.theme.StatusDownloading
import com.app.minnal.core.ui.theme.StatusSeeding

/**
 * Composable that displays download and upload speeds with directional arrows.
 *
 * Shows download speed with a down arrow and upload speed with an up arrow,
 * formatted as human-readable strings (e.g., "1.5 MB/s").
 *
 * @param downloadSpeed current download speed in bytes per second
 * @param uploadSpeed current upload speed in bytes per second
 * @param modifier optional modifier for the composable
 * @param textStyle the text style to apply; defaults to [MaterialTheme.typography.bodySmall]
 */
@Composable
fun SpeedDisplay(
    downloadSpeed: Long,
    uploadSpeed: Long,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Download speed with down arrow
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = textStyle.toSpanStyle().copy(color = StatusDownloading)
                ) {
                    append("\u2193 ")
                }
                withStyle(
                    style = textStyle.toSpanStyle().copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    append(downloadSpeed.toHumanReadableSpeed())
                }
            }
        )

        // Upload speed with up arrow
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = textStyle.toSpanStyle().copy(color = StatusSeeding)
                ) {
                    append("\u2191 ")
                }
                withStyle(
                    style = textStyle.toSpanStyle().copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    append(uploadSpeed.toHumanReadableSpeed())
                }
            }
        )
    }
}
