package com.budget.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Collections

/**
 * Whether the phone has a usable network, from the system's callbacks. The
 * app also treats any answer from the server as proof it is online, since the
 * callbacks can lag after the phone wakes.
 */
class NetworkMonitor(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val connected = Collections.synchronizedSet(HashSet<Network>())

    private fun snapshot(): Boolean {
        val manager = cm ?: return true
        val net = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _online = MutableStateFlow(snapshot())
    val online: StateFlow<Boolean> = _online

    init {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected.add(network)
                _online.value = true
            }

            override fun onLost(network: Network) {
                connected.remove(network)
                if (connected.isEmpty()) _online.value = snapshot()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm?.registerNetworkCallback(request, callback) }
    }

    fun confirmOnline() {
        _online.value = true
    }

    fun recheck() {
        _online.value = snapshot()
    }
}
