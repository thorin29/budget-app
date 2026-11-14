package com.budget.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) blocks reaching home-network addresses without the
 * ACCESS_LOCAL_NETWORK permission. Asked for only when the server address is
 * obviously local; a public address never triggers it.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun needsPermission(context: Context, server: String): Boolean {
        if (Build.VERSION.SDK_INT < 37) return false
        if (!isLikelyLocal(server)) return false
        return ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED
    }

    fun isLikelyLocal(server: String): Boolean {
        val host = ServerUrl.host(server) ?: return false
        if (host == "localhost") return true
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home") || host.endsWith(".internal")) {
            return true
        }
        val parts = host.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || parts[0] == 127 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 169 && parts[1] == 254)
    }
}
