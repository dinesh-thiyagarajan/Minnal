package com.app.minnal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.app.minnal.core.ui.theme.MinnalTheme
import com.app.minnal.navigation.MinnalNavGraph
import com.app.minnal.navigation.NavRoutes
import com.app.minnal.service.TorrentService

/**
 * Main activity for the Minnal torrent client.
 *
 * Handles:
 * - Setting up the Compose navigation graph
 * - Runtime permission requests (notifications, storage)
 * - File picker integration for .torrent files
 * - Intent handling for .torrent files and magnet: URIs
 * - Starting the torrent foreground service
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Parse incoming intent data
        val initialTorrentData = extractTorrentDataFromIntent(intent)
        val initialMagnetUri = extractMagnetUriFromIntent(intent)

        setContent {
            MinnalTheme {
                val context = LocalContext.current
                val navController = rememberNavController()

                // State for torrent file data picked by user
                var pickedTorrentData by remember { mutableStateOf<ByteArray?>(initialTorrentData) }
                var serviceStarted by remember { mutableStateOf(false) }

                // File picker launcher
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        val data = readBytesFromUri(it)
                        if (data != null) {
                            pickedTorrentData = data
                            navController.navigate(NavRoutes.ADD_TORRENT) {
                                launchSingleTop = true
                            }
                        }
                    }
                }

                // Notification permission launcher (Android 13+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Permission result handled; service will work without it */ }

                // Storage permission launcher (Android < 10)
                val storagePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* Permission results handled */ }

                // Request permissions on launch
                LaunchedEffect(Unit) {
                    // Request notification permission on Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotificationPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasNotificationPermission) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }

                    // Request storage permissions on older Android versions
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val permissions = mutableListOf<String>()

                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }

                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        if (permissions.isNotEmpty()) {
                            storagePermissionLauncher.launch(permissions.toTypedArray())
                        }
                    }
                }

                // Handle magnet URI from intent
                LaunchedEffect(initialMagnetUri) {
                    if (initialMagnetUri != null) {
                        navController.navigate(NavRoutes.ADD_TORRENT) {
                            launchSingleTop = true
                        }
                    }
                }

                MinnalNavGraph(
                    navController = navController,
                    onPickFile = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/x-bittorrent",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    torrentData = pickedTorrentData,
                    onStartService = {
                        if (!serviceStarted) {
                            startTorrentService()
                            serviceStarted = true
                        }
                    },
                    initialMagnetUri = initialMagnetUri
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle new intents when activity is already running
        // The activity will re-compose with the new intent data
    }

    /**
     * Extracts raw torrent file bytes from an intent containing a .torrent file URI.
     */
    private fun extractTorrentDataFromIntent(intent: Intent): ByteArray? {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return null
            val mimeType = contentResolver.getType(uri)

            // Check if it's a torrent file by MIME type or file extension
            if (mimeType == "application/x-bittorrent" ||
                uri.toString().endsWith(".torrent", ignoreCase = true)
            ) {
                return readBytesFromUri(uri)
            }
        }
        return null
    }

    /**
     * Extracts a magnet URI from an intent.
     */
    private fun extractMagnetUriFromIntent(intent: Intent): String? {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return null
            if (uri.scheme == "magnet") {
                return uri.toString()
            }
        }
        return null
    }

    /**
     * Reads all bytes from a content URI.
     */
    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Starts the torrent foreground service.
     */
    private fun startTorrentService() {
        val serviceIntent = TorrentService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
