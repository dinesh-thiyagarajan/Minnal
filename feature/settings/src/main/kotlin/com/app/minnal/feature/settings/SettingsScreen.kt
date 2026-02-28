package com.app.minnal.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.minnal.core.common.toHumanReadableSpeed
import org.koin.androidx.compose.koinViewModel

/**
 * Settings screen for configuring the torrent client.
 *
 * Displays categorized settings including storage, speed limits,
 * connections, and behavior toggles. Each setting opens an edit dialog
 * or toggles directly.
 *
 * @param onBack callback for back navigation
 * @param viewModel the ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state
    var showDialog by remember { mutableStateOf<SettingsDialog?>(null) }

    // Show error as snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Storage section
                SettingsSectionHeader(title = "Storage")
                ListItem(
                    headlineContent = { Text("Download Path") },
                    supportingContent = {
                        Text(
                            text = uiState.settings.downloadPath.ifEmpty { "Not set (using default)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Download path"
                        )
                    },
                    modifier = Modifier.clickable {
                        showDialog = SettingsDialog.DownloadPath(
                            currentValue = uiState.settings.downloadPath
                        )
                    }
                )
                HorizontalDivider()

                // Speed Limits section
                SettingsSectionHeader(title = "Speed Limits")
                ListItem(
                    headlineContent = { Text("Max Download Speed") },
                    supportingContent = {
                        Text(
                            text = if (uiState.settings.maxDownloadSpeed == 0L) {
                                "Unlimited"
                            } else {
                                uiState.settings.maxDownloadSpeed.toHumanReadableSpeed()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Max download speed"
                        )
                    },
                    modifier = Modifier.clickable {
                        showDialog = SettingsDialog.SpeedLimit(
                            title = "Max Download Speed",
                            currentValue = uiState.settings.maxDownloadSpeed,
                            isDownload = true
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Max Upload Speed") },
                    supportingContent = {
                        Text(
                            text = if (uiState.settings.maxUploadSpeed == 0L) {
                                "Unlimited"
                            } else {
                                uiState.settings.maxUploadSpeed.toHumanReadableSpeed()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = "Max upload speed"
                        )
                    },
                    modifier = Modifier.clickable {
                        showDialog = SettingsDialog.SpeedLimit(
                            title = "Max Upload Speed",
                            currentValue = uiState.settings.maxUploadSpeed,
                            isDownload = false
                        )
                    }
                )
                HorizontalDivider()

                // Connections section
                SettingsSectionHeader(title = "Connections")
                ListItem(
                    headlineContent = { Text("Max Connections") },
                    supportingContent = {
                        Text(
                            text = "${uiState.settings.maxConnections}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Max connections"
                        )
                    },
                    modifier = Modifier.clickable {
                        showDialog = SettingsDialog.IntegerValue(
                            title = "Max Connections",
                            currentValue = uiState.settings.maxConnections,
                            key = "max_connections"
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Max Per Torrent") },
                    supportingContent = {
                        Text(
                            text = "${uiState.settings.maxConnectionsPerTorrent}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Max per torrent"
                        )
                    },
                    modifier = Modifier.clickable {
                        showDialog = SettingsDialog.IntegerValue(
                            title = "Max Connections Per Torrent",
                            currentValue = uiState.settings.maxConnectionsPerTorrent,
                            key = "max_per_torrent"
                        )
                    }
                )
                HorizontalDivider()

                // Behavior section
                SettingsSectionHeader(title = "Behavior")
                ListItem(
                    headlineContent = { Text("Start on Add") },
                    supportingContent = {
                        Text(
                            text = "Start downloading immediately when a torrent is added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.settings.startOnAdd,
                            onCheckedChange = { viewModel.updateStartOnAdd(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Seeding Enabled") },
                    supportingContent = {
                        Text(
                            text = "Continue uploading after download completes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.settings.seedingEnabled,
                            onCheckedChange = { viewModel.updateSeedingEnabled(it) }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Handle dialogs
    when (val dialog = showDialog) {
        is SettingsDialog.DownloadPath -> {
            EditTextDialog(
                title = "Download Path",
                currentValue = dialog.currentValue,
                label = "Path",
                keyboardType = KeyboardType.Text,
                onConfirm = { value ->
                    viewModel.updateDownloadPath(value)
                    showDialog = null
                },
                onDismiss = { showDialog = null }
            )
        }
        is SettingsDialog.SpeedLimit -> {
            EditSpeedDialog(
                title = dialog.title,
                currentValueKBs = dialog.currentValue / 1024,
                onConfirm = { kbPerSec ->
                    val bytesPerSec = kbPerSec * 1024
                    if (dialog.isDownload) {
                        viewModel.updateMaxDownloadSpeed(bytesPerSec)
                    } else {
                        viewModel.updateMaxUploadSpeed(bytesPerSec)
                    }
                    showDialog = null
                },
                onDismiss = { showDialog = null }
            )
        }
        is SettingsDialog.IntegerValue -> {
            EditIntDialog(
                title = dialog.title,
                currentValue = dialog.currentValue,
                onConfirm = { value ->
                    when (dialog.key) {
                        "max_connections" -> viewModel.updateMaxConnections(value)
                        "max_per_torrent" -> viewModel.updateMaxConnectionsPerTorrent(value)
                    }
                    showDialog = null
                },
                onDismiss = { showDialog = null }
            )
        }
        null -> { /* No dialog shown */ }
    }
}

/**
 * Section header for grouping related settings.
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * Sealed class representing the different types of settings dialogs.
 */
private sealed class SettingsDialog {
    data class DownloadPath(val currentValue: String) : SettingsDialog()
    data class SpeedLimit(
        val title: String,
        val currentValue: Long,
        val isDownload: Boolean
    ) : SettingsDialog()
    data class IntegerValue(
        val title: String,
        val currentValue: Int,
        val key: String
    ) : SettingsDialog()
}

/**
 * Dialog for editing a text setting value.
 */
@Composable
private fun EditTextDialog(
    title: String,
    currentValue: String,
    label: String,
    keyboardType: KeyboardType,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for editing a speed limit value (in KB/s).
 */
@Composable
private fun EditSpeedDialog(
    title: String,
    currentValueKBs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(if (currentValueKBs == 0L) "" else currentValueKBs.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "Enter speed in KB/s (0 or empty for unlimited)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            text = newValue
                        }
                    },
                    label = { Text("Speed (KB/s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toLongOrNull() ?: 0L
                onConfirm(value)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for editing an integer setting value.
 */
@Composable
private fun EditIntDialog(
    title: String,
    currentValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        text = newValue
                    }
                },
                label = { Text("Value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toIntOrNull() ?: currentValue
                onConfirm(value.coerceAtLeast(1))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
