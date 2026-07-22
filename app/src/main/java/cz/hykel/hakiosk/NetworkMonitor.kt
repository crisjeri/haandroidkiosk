package cz.hykel.hakiosk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

class NetworkMonitor(context: Context, private val listener: Listener) {
    interface Listener {
        fun onNetworkStateChanged(available: Boolean)
    }

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private var lastAvailable: Boolean? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish(true)
        override fun onLost(network: Network) = publish(connectivityManager.activeNetwork != null)
        override fun onUnavailable() = publish(false)
    }

    fun start() {
        if (registered) return
        registered = true
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        publish(connectivityManager.activeNetwork != null)
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun publish(available: Boolean) {
        mainHandler.post {
            if (lastAvailable != available) {
                lastAvailable = available
                listener.onNetworkStateChanged(available)
            }
        }
    }
}
