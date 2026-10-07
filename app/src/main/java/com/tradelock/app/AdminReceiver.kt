package com.tradelock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Optional "Device admin" mode. While active, Android won't let TradeLock be
 * uninstalled or have its data cleared. It requests no other powers.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "This removes TradeLock's uninstall protection. Is this greed talking?"
}
