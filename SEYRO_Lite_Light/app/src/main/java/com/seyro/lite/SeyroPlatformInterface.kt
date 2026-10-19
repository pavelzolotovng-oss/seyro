package com.seyro.lite

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.system.OsConstants
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InetAddress
import java.net.NetworkInterface as JavaNetworkInterface
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * Android platform integration required by sing-box for finding and tracking
 * the real network underneath the VPN TUN.
 */
internal class SeyroPlatformInterface(private val vpn: VpnService) : PlatformInterface {
    private val connectivity = vpn.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val defaultNetwork = AtomicReference<Network?>(null)
    @Volatile private var defaultListener: InterfaceUpdateListener? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            DebugLog.i(vpn, "NETWORK", "onAvailable: network=$network; capabilities=${connectivity.getNetworkCapabilities(network)}; interface=${connectivity.getLinkProperties(network)?.interfaceName}")
            // Never replace the physical underlay with tun0.
            if (!isPhysicalNetwork(network)) {
                DebugLog.w(vpn, "NETWORK", "onAvailable отклонён: сеть не распознана как физическая; network=$network")
                return
            }
            defaultNetwork.set(network)
            DebugLog.i(vpn, "NETWORK", "Физическая сеть выбрана по onAvailable: network=$network")
            notifyDefaultInterface(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            DebugLog.i(vpn, "NETWORK", "onLinkPropertiesChanged: network=$network; interface=${linkProperties.interfaceName}; addresses=${linkProperties.linkAddresses}; routes=${linkProperties.routes.size}; capabilities=${connectivity.getNetworkCapabilities(network)}")
            if (isPhysicalNetwork(network)) {
                defaultNetwork.set(network)
                DebugLog.i(vpn, "NETWORK", "Физическая сеть подтверждена после обновления параметров: ${linkProperties.interfaceName}")
                notifyDefaultInterface(network)
            } else {
                DebugLog.w(vpn, "NETWORK", "Сеть пока не прошла физическую проверку после обновления параметров: $network")
            }
        }

        override fun onLost(network: Network) {
            DebugLog.w(vpn, "NETWORK", "onLost: network=$network; previousDefault=${defaultNetwork.get()}")
            if (defaultNetwork.compareAndSet(network, null)) {
                val replacement = findPhysicalDefaultNetwork()
                DebugLog.i(vpn, "NETWORK", "Потеряна активная сеть; замена=${replacement ?: "нет"}")
                defaultNetwork.set(replacement)
                notifyDefaultInterface(replacement)
            }
        }
    }

    private fun isPhysicalNetwork(network: Network): Boolean {
        val caps = connectivity.getNetworkCapabilities(network)
        if (caps == null) {
            DebugLog.d(vpn, "NETWORK", "isPhysicalNetwork=false: capabilities ещё отсутствуют для $network")
            return false
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            DebugLog.d(vpn, "NETWORK", "isPhysicalNetwork=false: Android пометил $network как VPN")
            return false
        }
        val props = connectivity.getLinkProperties(network)
        if (props == null) {
            DebugLog.d(vpn, "NETWORK", "isPhysicalNetwork=false: LinkProperties ещё отсутствуют для $network")
            return false
        }
        val name = props.interfaceName
        if (name == null) {
            DebugLog.d(vpn, "NETWORK", "isPhysicalNetwork=false: interfaceName отсутствует для $network")
            return false
        }
        if (name.startsWith("tun") || name.startsWith("wg")) {
            DebugLog.w(vpn, "NETWORK", "isPhysicalNetwork=false: виртуальный интерфейс $name")
            return false
        }
        val physical = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        DebugLog.i(vpn, "NETWORK", "Проверка физической сети: network=$network; interface=$name; physical=$physical; wifi=${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}; cellular=${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}; ethernet=${caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}; validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}; internet=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
        return physical
    }

    private fun findPhysicalDefaultNetwork(): Network? {
        val active = connectivity.activeNetwork
        DebugLog.i(vpn, "NETWORK", "Поиск физической сети: activeNetwork=$active; allNetworks=${connectivity.allNetworks.joinToString()}")
        if (active != null && isPhysicalNetwork(active)) {
            DebugLog.i(vpn, "NETWORK", "Физическая сеть найдена как activeNetwork=$active")
            return active
        }
        val fallback = connectivity.allNetworks.firstOrNull { isPhysicalNetwork(it) }
        DebugLog.i(vpn, "NETWORK", "Результат поиска физической сети: ${fallback ?: "не найдена"}")
        return fallback
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        // Keep outbound sockets outside our own VPN. Binding to the default
        // Android Network is handled by Android's default-network routing.
        if (!vpn.protect(fd)) error("Unable to protect sing-box socket")
    }

    override fun openTun(options: TunOptions): Int = (vpn as SeyroVpnService).openTunInterface(options)
    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner = error("Connection owner lookup is not enabled")

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultListener = listener
        val active = findPhysicalDefaultNetwork()
        if (active != null) {
            defaultNetwork.set(active)
            notifyDefaultInterface(active)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivity.registerDefaultNetworkCallback(networkCallback)
            } else {
                @Suppress("DEPRECATION")
                connectivity.registerNetworkCallback(
                    android.net.NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    networkCallback
                )
            }
            DebugLog.i(vpn, "NETWORK", "Монитор сети Android запущен; activeNetwork=${active != null}")
        } catch (e: Exception) {
            DebugLog.e(vpn, "NETWORK", "Не удалось запустить монитор сети: ${e.message}")
            // Enumeration still provides the current usable interfaces.
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultListener = null
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        defaultNetwork.set(null)
        DebugLog.i(vpn, "NETWORK", "Монитор сети Android остановлен")
    }

    private fun notifyDefaultInterface(network: Network?) {
        val listener = defaultListener ?: return
        if (network == null) {
            listener.updateDefaultInterface("", -1, false, false)
            DebugLog.e(vpn, "NETWORK", "Android не сообщил активную сеть")
            return
        }
        try {
            val properties = connectivity.getLinkProperties(network)
            val name = properties?.interfaceName
            DebugLog.i(vpn, "NETWORK", "notifyDefaultInterface: network=$network; interface=$name; propertiesPresent=${properties != null}")
            if (name.isNullOrBlank()) {
                listener.updateDefaultInterface("", -1, false, false)
                DebugLog.e(vpn, "NETWORK", "У активной сети отсутствует имя интерфейса")
                return
            }
            val index = JavaNetworkInterface.getByName(name)?.index ?: -1
            if (index < 0) {
                DebugLog.e(vpn, "NETWORK", "Не найден индекс активного интерфейса: $name")
                return
            }
            val caps = connectivity.getNetworkCapabilities(network)
            val expensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
            val constrained = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) == false
            listener.updateDefaultInterface(name, index, expensive, constrained)
            DebugLog.i(vpn, "NETWORK", "Активная сеть sing-box: $name (index=$index)")
        } catch (e: Exception) {
            DebugLog.e(vpn, "NETWORK", "Ошибка определения активной сети: ${e.message}")
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
        val networks = try { connectivity.allNetworks.toList() } catch (_: Exception) { emptyList() }
        val javaInterfaces = try {
            Collections.list(JavaNetworkInterface.getNetworkInterfaces())
        } catch (_: Exception) { emptyList() }

        for (network in networks) {
            try {
                val properties = connectivity.getLinkProperties(network) ?: continue
                val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
                val name = properties.interfaceName?.takeIf { it.isNotBlank() } ?: continue
                val physical = javaInterfaces.firstOrNull { it.name == name } ?: continue
                // Never expose the VPN's own TUN as an underlying network.
                if (name.startsWith("tun") || name.startsWith("wg")) continue

                val item = io.nekohasekai.libbox.NetworkInterface()
                item.name = name
                item.index = physical.index
                item.mtu = runCatching { physical.mtu }.getOrDefault(1500)
                item.dnsServer = stringIterator(properties.dnsServers.mapNotNull { it.hostAddress })
                item.gateway = stringIterator(
                    properties.routes
                        .filter { it.destination.prefixLength == 0 }
                        .mapNotNull { it.gateway }
                        .filterNot { it.isAnyLocalAddress }
                        .mapNotNull { it.hostAddress }
                )
                item.addresses = stringIterator(physical.interfaceAddresses.mapNotNull { address ->
                    val ip = address.address ?: return@mapNotNull null
                    "${ip.hostAddress}/${address.networkPrefixLength}"
                })
                item.type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                var flags = 0
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    flags = flags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                }
                if (physical.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
                if (physical.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
                if (physical.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
                item.flags = flags
                item.metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                result.add(item)
                DebugLog.d(vpn, "NETWORK", "Интерфейс sing-box: $name; addresses=${physical.interfaceAddresses.size}; dns=${properties.dnsServers.size}")
            } catch (e: Exception) {
                DebugLog.e(vpn, "NETWORK", "Пропущен интерфейс Android: ${e.message}")
            }
        }

        DebugLog.i(vpn, "NETWORK", "sing-box получил интерфейсов: ${result.size}")
        return object : NetworkInterfaceIterator {
            private val iterator = result.iterator()
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): io.nekohasekai.libbox.NetworkInterface = iterator.next()
        }
    }

    private fun stringIterator(values: List<String>): StringIterator {
        val snapshot = values.toList()
        val iterator = snapshot.iterator()
        return object : StringIterator {
            override fun len(): Int = snapshot.size
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): String = iterator.next()
        }
    }

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun clearDNSCache() {}
    override fun readWIFIState(): WIFIState? = null
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun usePlatformShell(): Boolean = false
    override fun checkPlatformShell() { error("Platform shell is not supported") }
    override fun openShellSession(user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int): ShellSession = error("Platform shell is not supported")
    override fun readSystemSSHHostKey(): String = error("System SSH host key is not supported")
    override fun lookupSFTPServer(): String = error("SFTP server is not supported")
    override fun tailscaleHostname(): String = Build.MODEL ?: "Android"
    override fun usePlatformBridge(): Boolean = false
    override fun createBridge(options: BridgeOptions?): BridgeSession = error("Platform bridge is not supported")
    override fun sendNotification(notification: Notification) {}
    override fun cancelNotification(identifier: String, typeID: Int) {}
    override fun lookupUser(username: String?): PlatformUser = error("Platform user lookup is not supported")
    override fun registerMyInterface(name: String?) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}
}
