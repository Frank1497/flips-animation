package dev.franklyn.foldscenes.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.franklyn.foldscenes.R

/** Reads the device location master switch. No permission required. */
object LocationState {
    fun isOn(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: false
}

/**
 * Whether this app holds WRITE_SECURE_SETTINGS, the signature permission that lets [SecureSettingsTurnOff]
 * actually flip the switch. Granted only via `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`;
 * the Play Store can never grant it at runtime. See docs/research/location-auto-off-spike.md once Phase 0 runs.
 */
fun hasWriteSecureSettings(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

/** One action the auto-off timer can take once the interval elapses. */
interface LocationAutoOffAction {
    /** Returns true if location was actually turned off (or the user was told to); false if the attempt failed. */
    fun act(context: Context): Boolean
}

/**
 * Direct write path: `Settings.Secure.LOCATION_MODE = 0`. Deprecated since API 28 but still a functioning
 * compatibility write on AOSP-derived ROMs as of this writing (unverified on this Motorola build — Phase 0).
 * Requires WRITE_SECURE_SETTINGS; silently fails (returns false) without it, callers must check
 * [hasWriteSecureSettings] first and fall back to [NotificationReminderAction] otherwise.
 */
class SecureSettingsTurnOffAction : LocationAutoOffAction {
    override fun act(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0)
    }.onFailure {
        Log.e(TAG, "LOCATION_MODE write failed: ${it.javaClass.name}: ${it.message}")
    }.getOrDefault(false)

    private companion object {
        const val TAG = "LocationAutoOff"
    }
}

/**
 * No-permission fallback for everyone who installed from the Play Store: posts a notification nudging the
 * user to turn location off themselves, since the app cannot do it without WRITE_SECURE_SETTINGS.
 */
class NotificationReminderAction : LocationAutoOffAction {
    override fun act(context: Context): Boolean {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.location_reminder_title))
            .setContentText(context.getString(R.string.location_reminder_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun ensureChannel(context: Context) {
        val manager = android.app.NotificationManager::class.java
            .let { context.getSystemService(it) } ?: return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.location_reminder_channel),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "location_autooff"
        const val NOTIFICATION_ID = 42
    }
}

/** Picks the real action: direct write when granted, notification reminder otherwise. */
fun locationAutoOffActionFor(context: Context): LocationAutoOffAction =
    if (hasWriteSecureSettings(context)) SecureSettingsTurnOffAction() else NotificationReminderAction()
