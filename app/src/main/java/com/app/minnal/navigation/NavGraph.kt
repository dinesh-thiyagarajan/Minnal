package com.app.minnal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.minnal.feature.addtorrent.AddTorrentScreen
import com.app.minnal.feature.settings.SettingsScreen
import com.app.minnal.feature.torrentlist.TorrentListScreen

/**
 * Navigation routes for the Minnal app.
 */
object NavRoutes {
    const val TORRENT_LIST = "torrent_list"
    const val ADD_TORRENT = "add_torrent"
    const val SETTINGS = "settings"
    const val TORRENT_DETAIL = "torrent_detail/{torrentId}"

    fun torrentDetail(torrentId: String): String = "torrent_detail/$torrentId"
}

/**
 * Main navigation graph for the Minnal torrent client app.
 *
 * Defines the navigation flow between the torrent list, add torrent,
 * settings, and torrent detail screens.
 *
 * @param navController the navigation controller managing the back stack
 * @param onPickFile callback triggered when the user wants to pick a .torrent file
 * @param torrentData optional raw bytes from a pre-selected .torrent file
 * @param onStartService callback to start the torrent foreground service
 * @param initialMagnetUri optional magnet URI to pre-fill on the add torrent screen
 */
@Composable
fun MinnalNavGraph(
    navController: NavHostController = rememberNavController(),
    onPickFile: () -> Unit,
    torrentData: ByteArray?,
    onStartService: () -> Unit,
    initialMagnetUri: String? = null
) {
    var pendingTorrentData by remember { mutableStateOf(torrentData) }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.TORRENT_LIST
    ) {
        composable(NavRoutes.TORRENT_LIST) {
            TorrentListScreen(
                onAddTorrent = {
                    pendingTorrentData = null
                    navController.navigate(NavRoutes.ADD_TORRENT) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(NavRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                onTorrentClick = { torrentId ->
                    navController.navigate(NavRoutes.torrentDetail(torrentId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(NavRoutes.ADD_TORRENT) {
            AddTorrentScreen(
                onPickFile = onPickFile,
                onBack = {
                    pendingTorrentData = null
                    navController.popBackStack()
                    onStartService()
                },
                torrentData = pendingTorrentData
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = NavRoutes.TORRENT_DETAIL,
            arguments = listOf(
                navArgument("torrentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val torrentId = backStackEntry.arguments?.getString("torrentId") ?: ""
            // Torrent detail screen - for now navigate back as a placeholder
            // since torrent detail was not requested as a separate feature module
            TorrentListScreen(
                onAddTorrent = {
                    navController.navigate(NavRoutes.ADD_TORRENT)
                },
                onSettingsClick = {
                    navController.navigate(NavRoutes.SETTINGS)
                },
                onTorrentClick = { }
            )
        }
    }

    // Handle initial magnet URI by navigating to add torrent screen
    if (initialMagnetUri != null) {
        navController.navigate(NavRoutes.ADD_TORRENT) {
            launchSingleTop = true
        }
    }

    // Handle torrent data by navigating to add torrent screen
    if (torrentData != null && torrentData.isNotEmpty()) {
        pendingTorrentData = torrentData
        navController.navigate(NavRoutes.ADD_TORRENT) {
            launchSingleTop = true
        }
    }
}
