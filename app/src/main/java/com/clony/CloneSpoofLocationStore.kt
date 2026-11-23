package com.clony

import android.content.Context

/** Stores the GPS coordinates automatically derived from each clone's proxy egress. */
internal data class CloneSpoofLocationSettings(
    val enabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasValidCoordinates: Boolean
        get() = latitude?.isFinite() == true && longitude?.isFinite() == true &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun normalized(): CloneSpoofLocationSettings = copy(
        enabled = enabled && hasValidCoordinates,
        latitude = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 },
        longitude = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 }
    ).let { normalized ->
        if (normalized.latitude == null || normalized.longitude == null) {
            normalized.copy(enabled = false, latitude = null, longitude = null)
        } else {
            normalized
        }
    }
}

internal object CloneSpoofLocationStore {
    private const val PREFERENCES = "clone_spoof_location_v1"
    private const val KEY_PREFIX = "spoof_location:"

    fun read(context: Context, packageName: String, userId: Int): CloneSpoofLocationSettings {
        if (!isValid(packageName) || userId < 0) return CloneSpoofLocationSettings()
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = cloneKey(packageName, userId)
        if (!preferences.contains("$key:enabled")) return CloneSpoofLocationSettings()
        return read(context, key)
    }

    fun write(
        context: Context,
        packageName: String,
        userId: Int,
        settings: CloneSpoofLocationSettings
    ): Boolean {
        if (!isValid(packageName) || userId < 0) return false
        return write(context, cloneKey(packageName, userId), packageName, settings)
    }

    fun delete(context: Context, packageName: String, userId: Int): Boolean {
        if (!isValid(packageName) || userId < 0) return false
        val key = cloneKey(packageName, userId)
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove("$key:enabled")
            .remove("$key:latitude")
            .remove("$key:longitude")
            .commit()
    }

    private fun read(context: Context, key: String): CloneSpoofLocationSettings {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return CloneSpoofLocationSettings(
            enabled = preferences.getBoolean("$key:enabled", false),
            latitude = preferences.getString("$key:latitude", null)?.toDoubleOrNull(),
            longitude = preferences.getString("$key:longitude", null)?.toDoubleOrNull()
        ).normalized()
    }

    private fun write(
        context: Context,
        key: String,
        packageName: String,
        settings: CloneSpoofLocationSettings
    ): Boolean {
        if (!isValid(packageName)) return false
        val normalized = settings.normalized()
        val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$key:enabled", normalized.enabled)
        if (normalized.hasValidCoordinates) {
            editor.putString("$key:latitude", normalized.latitude.toString())
            editor.putString("$key:longitude", normalized.longitude.toString())
        } else {
            editor.remove("$key:latitude")
            editor.remove("$key:longitude")
        }
        return editor.commit()
    }

    private fun cloneKey(packageName: String, userId: Int) = "${KEY_PREFIX}clone:$packageName:$userId"
    private fun isValid(packageName: String) = packageName.matches(Regex("[A-Za-z0-9_.]+"))
}
