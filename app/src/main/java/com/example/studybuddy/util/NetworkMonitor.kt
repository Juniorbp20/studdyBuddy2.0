package com.example.studybuddy.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor private constructor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var wasOffline = !checkInitialConnectivity()
    private val listeners = mutableListOf<() -> Unit>()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previouslyOffline = !_isOnline.value
                _isOnline.value = true
                if (previouslyOffline || wasOffline) {
                    wasOffline = false
                    notifyNetworkRestored()
                }
            }

            override fun onLost(network: Network) {
                val stillHasNetwork = checkCurrentConnectivity()
                _isOnline.value = stillHasNetwork
                if (!stillHasNetwork) {
                    wasOffline = true
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val previouslyOffline = !_isOnline.value
                _isOnline.value = hasInternet
                if (hasInternet && (previouslyOffline || wasOffline)) {
                    wasOffline = false
                    notifyNetworkRestored()
                }
            }
        })
    }

    private fun checkInitialConnectivity(): Boolean {
        return checkCurrentConnectivity()
    }

    private fun checkCurrentConnectivity(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun addOnNetworkRestoredListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeOnNetworkRestoredListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyNetworkRestored() {
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it.invoke() }
    }

    companion object {
        @Volatile
        private var INSTANCE: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun isConnected(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) {
                false
            }
        }
    }
}
