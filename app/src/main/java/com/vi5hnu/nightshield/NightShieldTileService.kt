package com.vi5hnu.nightshield

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NightShieldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = OverlayHelpers.areOverlaysActive(this)
        if (isRunning) {
            stopService(Intent(this, NightShieldService::class.java))
            OverlayHelpers.setOverlaysActive(this, false)
        } else {
            startForegroundService(Intent(this, NightShieldService::class.java))
            OverlayHelpers.setOverlaysActive(this, true)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = OverlayHelpers.areOverlaysActive(this)
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        tile.updateTile()
    }
}
