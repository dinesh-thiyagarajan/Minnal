package com.app.minnal.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.app.minnal.service.TorrentService

/**
 * BroadcastReceiver that listens for BOOT_COMPLETED to optionally restart
 * torrent downloads after a device reboot.
 *
 * When the device finishes booting, this receiver starts the [TorrentService]
 * as a foreground service so that previously active torrents can resume.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = TorrentService.startIntent(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
