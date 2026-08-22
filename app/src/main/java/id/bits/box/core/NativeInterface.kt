package id.bits.box

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.system.OsConstants
import id.bits.box.BitsBoxApp
import id.bits.box.bg.ServiceNotification
import id.bits.box.database.DataStore
import id.bits.box.database.BitsBoxDatabase
import id.bits.box.ktx.Logs
import id.bits.box.ktx.app
import id.bits.box.ktx.runOnDefaultDispatcher
import id.bits.box.utils.PackageCache
import libcore.BITSBoxInterface
import libcore.BoxPlatformInterface
import libcore.Libcore
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.NetworkInterface

class NativeInterface : BoxPlatformInterface, BITSBoxInterface {

    //  libbox interface

    override fun autoDetectInterfaceControl(fd: Int) {
        DataStore.vpnService?.protect(fd)
    }

    override fun openTun(singTunOptionsJson: String, tunPlatformOptionsJson: String): Long {
        if (DataStore.vpnService == null) {
            throw Exception("no VpnService")
        }
        return DataStore.vpnService!!.startVpn(singTunOptionsJson, tunPlatformOptionsJson).toLong()
    }

    override fun useProcFS(): Boolean {
        return false
    }

    override fun findConnectionOwner(
        ipProto: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int
    ): Int {
        return BitsBoxApp.connectivity.getConnectionOwnerUid(
            ipProto, InetSocketAddress(srcIp, srcPort), InetSocketAddress(destIp, destPort)
        )
    }

    override fun packageNameByUid(uid: Int): String {
        PackageCache.awaitLoadSync()

        if (uid <= 1000L) {
            return "android"
        }

        val packageNames = PackageCache.uidMap[uid]
        if (!packageNames.isNullOrEmpty()) for (packageName in packageNames) {
            return packageName
        }

        error("unknown uid $uid")
    }

    override fun uidByPackageName(packageName: String): Int {
        PackageCache.awaitLoadSync()
        return PackageCache[packageName] ?: 0
    }

    override fun wifiState(): String {
        val connectivity = app.applicationContext.getSystemService(ConnectivityManager::class.java)
        val connectionInfo = if (android.os.Build.VERSION.SDK_INT >= 31) {
            connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
                ?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            (app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
        }
        if (connectionInfo == null) return ","
        return "${connectionInfo.ssid},${connectionInfo.bssid}"
    }

    override fun defaultInterfaceName(): String {
        val network = BitsBoxApp.underlyingNetwork
            ?: BitsBoxApp.connectivity.activeNetwork
            ?: return ""
        val name = BitsBoxApp.connectivity.getLinkProperties(network)?.interfaceName ?: ""
        // Once our own VPN is up, the app's active network IS the VPN, so this
        // would return e.g. "tun0". Reporting it makes sing-box select the TUN
        // as default interface; selectInterfaces then excludes it (myInterfaces)
        // and every dial fails with "no available network interface". Report an
        // empty name instead so libcore keeps/retries a physical interface.
        if (name.startsWith("tun") || name.startsWith("tproxy") || name.startsWith("utun")) {
            return ""
        }
        return name
    }

    // Enumerates the device network interfaces via ConnectivityManager
    // (framework API, not blocked by SELinux) as a JSON array for the Go
    // side. On strict-SELinux devices both Go-side sources fail:
    // net.Interfaces() (netlink RTM_GETLINK) with EPERM and /sys/class/net
    // with "permission denied". Per-field failures are logged and never
    // drop the whole entry (pattern from husi).
    override fun networkInterfacesJSON(): String {
        val result = JSONArray()
        try {
            val connectivity = BitsBoxApp.connectivity
            @Suppress("DEPRECATION") val networks = connectivity.allNetworks
            val javaInterfaces = runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            }.getOrElse { e ->
                Logs.w("networkInterfacesJSON: getNetworkInterfaces failed", e)
                emptyList()
            }
            for (network in networks) {
                try {
                    val linkProperties = connectivity.getLinkProperties(network) ?: continue
                    val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
                    val name = linkProperties.interfaceName ?: continue
                    // Skip our own VPN interface: it must never be a dial
                    // candidate (see isVirtualInterfaceName in libcore).
                    if (name.startsWith("tun") || name.startsWith("tproxy") || name.startsWith("utun")) {
                        continue
                    }
                    val entry = JSONObject()
                    entry.put("name", name)
                    val javaInterface = javaInterfaces.find { it.name == name }
                    if (javaInterface != null) {
                        runCatching { entry.put("index", javaInterface.index) }
                            .onFailure { e -> Logs.w("networkInterfacesJSON: index failed for $name", e) }
                        runCatching { entry.put("mtu", javaInterface.mtu) }
                            .onFailure { e -> Logs.w("networkInterfacesJSON: mtu failed for $name", e) }
                    }
                    entry.put("type", when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                        else -> "other"
                    })
                    var flags = 0
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                    }
                    if (javaInterface != null) {
                        runCatching {
                            if (javaInterface.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
                            if (javaInterface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                            if (javaInterface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
                        }.onFailure { e -> Logs.w("networkInterfacesJSON: flags failed for $name", e) }
                    }
                    entry.put("flags", flags)
                    val dns = JSONArray()
                    runCatching {
                        for (server in linkProperties.dnsServers) {
                            server.hostAddress?.let { dns.put(it) }
                        }
                    }.onFailure { e -> Logs.w("networkInterfacesJSON: dns failed for $name", e) }
                    entry.put("dns", dns)
                    entry.put(
                        "metered",
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    )
                    result.put(entry)
                } catch (e: Exception) {
                    Logs.w("networkInterfacesJSON: entry failed", e)
                }
            }
        } catch (e: Exception) {
            Logs.e("networkInterfacesJSON failed", e)
        }
        return result.toString()
    }

    // BITS Box interface

    override fun useOfficialAssets(): Boolean {
        // false: APK hanya mengekstrak aset saat file belum ada (first install).
        // Jangan biarkan ekstraksi menimpa aset yang sudah di-download user
        // (mis. variant Full) hanya karena version file bundled lebih baru.
        return false
    }

    override fun selector_OnProxySelected(selectorTag: String, tag: String) {
        if (selectorTag != "proxy") {
            Logs.d("other selector: $selectorTag")
            return
        }
        Libcore.resetAllConnections(true)
        DataStore.baseService?.apply {
            runOnDefaultDispatcher {
                val id = data.proxy!!.config.profileTagMap
                    .filterValues { it == tag }.keys.firstOrNull() ?: -1
                val ent = BitsBoxDatabase.proxyDao.getById(id) ?: return@runOnDefaultDispatcher
                // traffic & title
                data.proxy?.apply {
                    looper?.selectMain(id)
                    displayProfileName = ServiceNotification.genTitle(ent)
                    data.notification?.postNotificationTitle(displayProfileName)
                }
                // post binder
                data.binder.broadcast { b ->
                    b.cbSelectorUpdate(id)
                }
            }
        }
    }

}
