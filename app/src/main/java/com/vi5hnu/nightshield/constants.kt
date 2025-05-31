package com.vi5hnu.nightshield

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.edit

object OverlayHelpers {
    private const val OVERLAYS_ACTIVE_PREF = "overlays_active"
    private const val PREFS_NAME = "overlay_prefs"

    fun setOverlaysActive(context: Context, isActive: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putBoolean(OVERLAYS_ACTIVE_PREF, isActive)
        }
    }

    fun areOverlaysActive(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(OVERLAYS_ACTIVE_PREF, false)
    }

    fun dispose(context: Context){
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit {
            remove(OVERLAYS_ACTIVE_PREF)
        }
    }
}