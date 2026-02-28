package com.app.minnal.feature.addtorrent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.minnal.core.common.toHumanReadableSize
import com.app.minnal.core.domain.model.TorrentFileInfo
import org.koin.androidx.compose.koinViewModel

/**
 * Add torrent screen allowing users to add a torrent from a file or magnet link.
 *
 * Features:
 * - Two tabs: "Torrent File" and "Magnet Link"
 * - File picker integration for selecting .torrent files
 * - Magnet link text input
 * - Parsed torrent details display
 * - Save path configuration
 * - Download button
 *
 * @param onPickFile callback to trigger the file picker
 * @param onBack callback for back navigation
 * @param torrentData optional raw bytes of a pre-selected .torrent file
 * @param viewModel the ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTorrentScreen(
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    torrentData: ByteArray? = null,
    viewModel: AddTorrentViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Parse torrent data when provided
    LaunchedEffect(torrentData) {
        torrentData?.let { data ->
            if (data.isNotEmpty()) {
                viewModel.parseTorrentFile(data)
            }
        }
    }

    // Navigate back when torrent is added
    LaunchedEffect(uiState.isAdded) {
        if (uiState.isAdded) {
            onBack()
        }
    }

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
                title = { Text("Add Torrent") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            val selectedTabIndex = if (uiState.mode == AddMode.FILE) 0 else 1
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { viewModel.setMode(AddMode.FILE) },
                    text = { Text("Torrent File") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "Torrent File"
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { viewModel.setMode(AddMode.MAGNET) },
                    text = { Text("Magnet Link") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Magnet Link"
                        )
                    }
                )
            }

            when (uiState.mode) {
                AddMode.FILE -> {
                    TorrentFileTab(
                        uiState = uiState,
                        onPickFile = onPickFile,
                        onAddTorrent = { viewModel.addTorrent() }
                    )
                }
                AddMode.MAGNET -> {
                    MagnetLinkTab(
                        uiState = uiState,
                        onMagnetLinkChange = { viewModel.setMagnetLink(it) },
                        onAddMagnet = { viewModel.addTorrent() }
                    )
                }
            }
        }
    }
}

/**
 * Tab content for adding a torrent from a .torrent file.
 */
@Composable
private fun TorrentFileTab(
    uiState: AddTorrentUiState,
    onPickFile: () -> Unit,
    onAddTorrent: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // File picker button
            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Pick file",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.torrentName.isNotEmpty()) {
                        "Change File"
                    } else {
                        "Select .torrent File"
                    }
                )
            }
        }

        // Show parsed torrent details
        if (uiState.torrentName.isNotEmpty()) {
            item {
                TorrentDetailsCard(uiState = uiState)
            }

            // File list
            if (uiState.files.isNotEmpty()) {
                item {
                    Text(
                        text = "Files (${uiState.fileCount})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.files) { file ->
                    FileListItem(file = file)
                }
            }

            // Save path
            item {
                OutlinedTextField(
                    value = uiState.savePath,
                    onValueChange = { },
                    label = { Text("Save Path") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Save path"
                        )
                    }
                )
            }

            // Download button
            item {
                Button(
                    onClick = onAddTorrent,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && uiState.torrentData != null
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download")
                }
            }
        }
    }
}

/**
 * Tab content for adding a torrent from a magnet link.
 */
@Composable
private fun MagnetLinkTab(
    uiState: AddTorrentUiState,
    onMagnetLinkChange: (String) -> Unit,
    onAddMagnet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Magnet link input
        OutlinedTextField(
            value = uiState.magnetLink,
            onValueChange = onMagnetLinkChange,
            label = { Text("Magnet Link") },
            placeholder = { Text("magnet:?xt=urn:btih:...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "Magnet link"
                )
            },
            singleLine = false,
            maxLines = 5
        )

        // Save path display
        OutlinedTextField(
            value = uiState.savePath,
            onValueChange = { },
            label = { Text("Save Path") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Save path"
                )
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Download button
        Button(
            onClick = onAddMagnet,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && uiState.magnetLink.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Download",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download")
        }
    }
}

/**
 * Card displaying parsed torrent details.
 */
@Composable
private fun TorrentDetailsCard(uiState: AddTorrentUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = uiState.torrentName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Size: ${uiState.torrentSize.toHumanReadableSize()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${uiState.fileCount} file${if (uiState.fileCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * List item for displaying a single file within a torrent.
 */
@Composable
private fun FileListItem(file: TorrentFileInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = "File",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = file.size.toHumanReadableSize(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
