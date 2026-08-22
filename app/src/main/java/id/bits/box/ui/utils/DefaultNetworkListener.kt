package id.bits.box.utils

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import id.bits.box.BitsBoxApp
import id.bits.box.ktx.Logs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.net.UnknownHostException

object DefaultNetworkListener {
    private sealed class NetworkMessage {
        class Start(val key: Any, val listener: (Network?) -> Unit) : NetworkMessage()
        class Get : NetworkMessage() {
            val response = CompletableDeferred<Network>()
        }

        class Stop(val key: Any) : NetworkMessage()

        class Put(val network: Network) : NetworkMessage()
        class Update(val network: Network) : NetworkMessage()
        class Lost(val network: Network) : NetworkMessage()
    }

    private val networkChannel = Channel<NetworkMessage>(Channel.UNLIMITED)

    init {
        BitsBoxApp.application.applicationScope.launch(Dispatchers.Unconfined) {
            val listeners = mutableMapOf<Any, (Network?) -> Unit>()
            var network: Network? = null
            val pendingRequests = arrayListOf<NetworkMessage.Get>()
            for (message in networkChannel) when (message) {
                is NetworkMessage.Start -> {
                    if (listeners.isEmpty()) register()
                    listeners[message.key] = message.listener
                    if (network != null) message.listener(network)
                }
                is NetworkMessage.Get -> {
                    check(listeners.isNotEmpty()) { "Getting network without any listeners is not supported" }
                    if (network == null) pendingRequests += message else message.response.complete(network)
                }
                is NetworkMessage.Stop -> if (listeners.isNotEmpty() &&
                    listeners.remove(message.key) != null && listeners.isEmpty()
                ) {
                    network = null
                    unregister()
                }

                is NetworkMessage.Put -> {
                    network = message.network
                    pendingRequests.forEach { it.response.complete(message.network) }
                    pendingRequests.clear()
                    listeners.values.forEach { it(network) }
                }
                is NetworkMessage.Update -> if (network == message.network) listeners.values.forEach { it(network) }
                is NetworkMessage.Lost -> if (network == message.network) {
                    network = null
                    listeners.values.forEach { it(null) }
                }
            }
        }
    }

    suspend fun start(key: Any, listener: (Network?) -> Unit) =
        networkChannel.send(NetworkMessage.Start(key, listener))

    suspend fun get() = if (fallback) {
        BitsBoxApp.connectivity.activeNetwork
            ?: throw UnknownHostException() // failed to listen, return current if available
    } else NetworkMessage.Get().run {
        networkChannel.send(this)
        response.await()
    }

    suspend fun stop(key: Any) = networkChannel.send(NetworkMessage.Stop(key))

    // NB: this runs in ConnectivityThread, and this behavior cannot be changed until API 26
    private object Callback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) =
            runBlocking { networkChannel.send(NetworkMessage.Put(network)) }

        override fun onCapabilitiesChanged(
            network: Network, networkCapabilities: NetworkCapabilities
        ) { // it's a good idea to refresh capabilities
            runBlocking { networkChannel.send(NetworkMessage.Update(network)) }
        }

        override fun onLost(network: Network) =
            runBlocking { networkChannel.send(NetworkMessage.Lost(network)) }
    }

    private var fallback = false
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return VPN interface since Android P DP1:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately, we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    @SuppressLint("NewApi") // each branch is gated by Build.VERSION.SDK_INT
    private fun register() {
        try {
            fallback = false
            when (Build.VERSION.SDK_INT) {
                in 31..Int.MAX_VALUE -> {
                    BitsBoxApp.connectivity.registerBestMatchingNetworkCallback(
                        request, Callback, mainHandler
                    )
                }
                in 28 until 31 -> {  // we want REQUEST here instead of LISTEN
                    BitsBoxApp.connectivity.requestNetwork(request, Callback, mainHandler)
                }
                in 26 until 28 -> {
                    BitsBoxApp.connectivity.registerDefaultNetworkCallback(Callback, mainHandler)
                }
                in 24 until 26 -> {
                    BitsBoxApp.connectivity.registerDefaultNetworkCallback(Callback)
                }
                else -> {
                    BitsBoxApp.connectivity.requestNetwork(request, Callback)
                    // known bug on API 23: https://stackoverflow.com/a/33509180/2245107
                }
            }
        } catch (e: Exception) {
            Logs.w(e)
            fallback = true
        }
    }

    private fun unregister() = BitsBoxApp.connectivity.unregisterNetworkCallback(Callback)
}
