package com.app.minnal.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.app.minnal.core.common.toHumanReadableSize

/**
 * Composable for displaying file size progress in the format "downloaded / total".
 *
 * Shows the current downloaded size and total size as human-readable strings
 * (e.g., "256 MB / 1.2 GB"), with the separator styled in a muted color.
 *
 * @param downloadedSize the number of bytes downloaded so far
 * @param totalSize the total number of bytes
 * @param modifier optional modifier for the composable
 * @param textStyle the text style to apply; defaults to [MaterialTheme.typography.bodySmall]
 */
@Composable
fun FileSizeText(
    downloadedSize: Long,
    totalSize: Long,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                style = textStyle.toSpanStyle().copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            ) {
                append(downloadedSize.toHumanReadableSize())
            }
            withStyle(
                style = textStyle.toSpanStyle().copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                append(" / ")
            }
            withStyle(
                style = textStyle.toSpanStyle().copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            ) {
                append(totalSize.toHumanReadableSize())
            }
        },
        modifier = modifier
    )
}
