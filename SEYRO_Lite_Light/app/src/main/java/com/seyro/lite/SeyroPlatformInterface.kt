package com.seyro.lite

import android.net.VpnService
import android.os.Build
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborEntryIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

/**
 * Minimal Android platform adapter for SEYRO's sing-box runtime.
 * Unsupported optional platform integrations deliberately fail closed.
 */
internal class SeyroPlatformInterface(private val vpn: VpnService) : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun autoDetectInterfaceControl(fd: Int) { if (!vpn.protect(fd)) error("Unable to protect sing-box socket") }
    override fun openTun(options: TunOptions): Int = (vpn as SeyroVpnService).openTunInterface(options)
    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int): ConnectionOwner = error("Connection owner lookup is not enabled")
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) { DebugLog.i(vpn, "NETWORK", "Запрошен монитор интерфейсов sing-box") }
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) { DebugLog.i(vpn, "NETWORK", "Монитор интерфейсов sing-box остановлен") }
    override fun getInterfaces(): NetworkInterfaceIterator { DebugLog.e(vpn, "NETWORK", "sing-box запросил список интерфейсов, но Android adapter его не предоставляет"); error("Interface enumeration is not enabled") }
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
    override fun tailscaleHostname(): String = android.os.Build.MODEL ?: "Android"
    override fun usePlatformBridge(): Boolean = false
    override fun createBridge(options: BridgeOptions?): BridgeSession = error("Platform bridge is not supported")
    override fun sendNotification(notification: Notification) {}
    override fun cancelNotification(identifier: String, typeID: Int) {}
    override fun lookupUser(username: String?): PlatformUser = error("Platform user lookup is not supported")
    override fun registerMyInterface(name: String?) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}
}
