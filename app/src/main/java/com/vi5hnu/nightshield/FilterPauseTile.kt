package com.vi5hnu.nightshield

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * What the Pause tile shows, kept free of Android types so it is unit-testable.
 * The tile service only maps this onto a [Tile].
 */
internal object PauseTileModel {

    enum class Mode {
        /** The filter is off, so there is nothing to pause; the system greys the tile out. */
        UNAVAILABLE,
        /** The filter is on; a tap pauses it. */
        READY,
        /** Paused; a tap brings the filter back now. */
        PAUSED,
    }

    data class Look(val mode: Mode, val subtitle: String)

    fun look(filterRunning: Boolean, remainingMs: Long, duration: FilterPause.Duration): Look = when {
        !filterRunning -> Look(Mode.UNAVAILABLE, "Filter is off")
        remainingMs > 0L -> Look(Mode.PAUSED, "Back in ${countdown(remainingMs)}")
        else -> Look(Mode.READY, "For ${duration.label}")
    }

    /** "0:45", "4:05"; seconds round up so the last second reads "0:01", never "0:00". */
    internal fun countdown(remainingMs: Long): String {
        val totalSeconds = (remainingMs + 999L) / 1000L
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

/**
 * Quick Settings tile that pauses the filter for payment and banking apps: the third way in,
 * beside the notification and the in-app button. The tile works where those fall short. It is one
 * tap on every Android version, Android 16's notification grouping can't hide it, and it works
 * with notifications denied.
 *
 * It only flips [FilterPause]; [NightShieldService] removes and restores the overlay window, as
 * it does for the other two entry points. While the shade is open the tile follows the filter's
 * state and counts down a running pause each second. It does nothing while the panel is closed.
 */
class FilterPauseTileService : TileService() {

    /** Lives from onStartListening to onStopListening, i.e. only while the tile is visible. */
    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningScope?.cancel()
        listeningScope = MainScope().also { scope ->
            scope.launch {
                combine(
                    NightShieldManager.isFilterActive,
                    FilterPause.pausedUntilElapsedMs,
                    FilterPause.durationChanges,
                ) { _, _, _ -> }.collectLatest {
                    render()
                    // Tick on whole seconds of the remaining time so the countdown never skips.
                    while (FilterPause.remainingMs() > 0L) {
                        delay(FilterPause.remainingMs() % 1_000L + 1L)
                        render()
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        listeningScope?.cancel()
        listeningScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Unavailable tiles get no clicks, but the filter may have stopped since the last render.
        if (!NightShieldService.isRunning) return render()
        if (FilterPause.isPaused) FilterPause.resume() else FilterPause.pause(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val look = PauseTileModel.look(
            filterRunning = NightShieldService.isRunning,
            remainingMs = FilterPause.remainingMs(),
            duration = FilterPause.duration(this),
        )
        tile.state = when (look.mode) {
            PauseTileModel.Mode.UNAVAILABLE -> Tile.STATE_UNAVAILABLE
            PauseTileModel.Mode.READY -> Tile.STATE_INACTIVE
            PauseTileModel.Mode.PAUSED -> Tile.STATE_ACTIVE
        }
        tile.label = getString(R.string.qs_pause_tile_label)
        tile.subtitle = look.subtitle
        tile.updateTile()
    }

    companion object {
        /** Android 13+ can offer to add a tile from inside the app; older versions can't. */
        val canRequestAdd: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /**
         * Shows the system "Add tile to Quick Settings?" dialog (Android 13+). Says so when the
         * tile is already there, because the system then shows no dialog at all.
         */
        fun requestAdd(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
            statusBar.requestAddTileService(
                ComponentName(context, FilterPauseTileService::class.java),
                context.getString(R.string.qs_pause_tile_label),
                Icon.createWithResource(context, R.drawable.ic_pause_24),
                context.mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                    Toast.makeText(context, "The Pause tile is already in Quick Settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
