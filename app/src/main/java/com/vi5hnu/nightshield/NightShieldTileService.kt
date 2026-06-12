package com.vi5hnu.nightshield

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NightShieldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Single control surface — the service owns the active flag and widget refresh.
        NightShieldController.toggle(this)
        updateTile()
        NightShieldWidgetProvider.updateWidget(this)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = OverlayHelpers.areOverlaysActive(this)
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_label)
        tile.updateTile()
    }
}
