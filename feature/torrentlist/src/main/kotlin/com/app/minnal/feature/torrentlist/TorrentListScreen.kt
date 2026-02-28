package com.app.minnal.feature.torrentlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.model.TorrentStatus
import org.koin.androidx.compose.koinViewModel

/**
 * Torrent list screen displaying all torrents with their current state.
 *
 * Features:
 * - TopAppBar with app title and settings navigation
 * - LazyColumn listing all active torrents
 * - Long press context menu for torrent actions
 * - FAB for adding new torrents
 * - Empty state when no torrents are present
 * - Error snackbar for displaying error messages
 *
 * @param onAddTorrent callback when user wants to add a new torrent
 * @param onSettingsClick callback when user taps the settings icon
 * @param onTorrentClick callback when user taps a torrent item
 * @param viewModel the ViewModel for this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentListScreen(
    onAddTorrent: () -> Unit,
    onSettingsClick: () -> Unit,
    onTorrentClick: (String) -> Unit,
    viewModel: TorrentListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Context menu state
    var selectedTorrent by remember { mutableStateOf<Torrent?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    // Remove confirmation dialog state
    var showRemoveDialog by remember { mutableStateOf(false) }
    var torrentToRemove by remember { mutableStateOf<Torrent?>(null) }
    var removeWithFiles by remember { mutableStateOf(false) }

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Minnal",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTorrent,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Torrent",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.torrents.isEmpty() -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.torrents.isEmpty() -> {
                    // Empty state
                    EmptyState(onAddTorrent = onAddTorrent)
                }

                else -> {
                    // Torrent list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.torrents,
                            key = { it.id }
                        ) { torrent ->
                            Box {
                                TorrentListItem(
                                    torrent = torrent,
                                    onClick = { onTorrentClick(torrent.id) },
                                    onLongClick = {
                                        selectedTorrent = torrent
                                        showContextMenu = true
                                    }
                                )

                                // Context menu
                                if (showContextMenu && selectedTorrent?.id == torrent.id) {
                                    TorrentContextMenu(
                                        torrent = torrent,
                                        expanded = true,
                                        onDismiss = {
                                            showContextMenu = false
                                            selectedTorrent = null
                                        },
                                        onPause = {
                                            viewModel.pauseTorrent(torrent.id)
                                            showContextMenu = false
                                            selectedTorrent = null
                                        },
                                        onResume = {
                                            viewModel.resumeTorrent(torrent.id)
                                            showContextMenu = false
                                            selectedTorrent = null
                                        },
                                        onRemove = {
                                            torrentToRemove = torrent
                                            removeWithFiles = false
                                            showRemoveDialog = true
                                            showContextMenu = false
                                            selectedTorrent = null
                                        },
                                        onRemoveWithFiles = {
                                            torrentToRemove = torrent
                                            removeWithFiles = true
                                            showRemoveDialog = true
                                            showContextMenu = false
                                            selectedTorrent = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Remove confirmation dialog
    if (showRemoveDialog && torrentToRemove != null) {
        RemoveConfirmationDialog(
            torrentName = torrentToRemove!!.name,
            deleteFiles = removeWithFiles,
            onConfirm = {
                viewModel.removeTorrent(torrentToRemove!!.id, removeWithFiles)
                showRemoveDialog = false
                torrentToRemove = null
            },
            onDismiss = {
                showRemoveDialog = false
                torrentToRemove = null
            }
        )
    }
}

/**
 * Context menu displayed on long press of a torrent item.
 */
@Composable
private fun TorrentContextMenu(
    torrent: Torrent,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRemoveWithFiles: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (torrent.status == TorrentStatus.PAUSED) {
            DropdownMenuItem(
                text = { Text("Resume") },
                onClick = onResume,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Resume"
                    )
                }
            )
        } else if (torrent.status == TorrentStatus.DOWNLOADING || torrent.status == TorrentStatus.SEEDING) {
            DropdownMenuItem(
                text = { Text("Pause") },
                onClick = onPause,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause"
                    )
                }
            )
        }

        DropdownMenuItem(
            text = { Text("Remove") },
            onClick = onRemove,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove"
                )
            }
        )

        DropdownMenuItem(
            text = { Text("Remove with files") },
            onClick = onRemoveWithFiles,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = "Remove with files"
                )
            }
        )
    }
}

/**
 * Confirmation dialog for removing a torrent.
 */
@Composable
private fun RemoveConfirmationDialog(
    torrentName: String,
    deleteFiles: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Remove Torrent")
        },
        text = {
            Text(
                text = if (deleteFiles) {
                    "Are you sure you want to remove \"$torrentName\" and delete all downloaded files? This action cannot be undone."
                } else {
                    "Are you sure you want to remove \"$torrentName\"? Downloaded files will be kept."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Remove",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

/**
 * Empty state displayed when there are no torrents.
 */
@Composable
private fun EmptyState(onAddTorrent: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = "No torrents",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Torrents",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap the + button to add a torrent\nor magnet link to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
