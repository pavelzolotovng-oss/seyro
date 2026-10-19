package com.seyro.lite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * SEYRO VPN runtime backed by sing-box/libbox. No Xray or HEV engine is used.
 */
class SeyroVpnService : VpnService(), CommandServerHandler {
    companion object {
        const val ACTION_START = "com.seyro.lite.START_VPN"
        const val ACTION_STOP = "com.seyro.lite.STOP_VPN"
        const val EXTRA_CONFIG = "xray_config"
        const val EXTRA_TRANSPORT = "transport"
        const val EXTRA_BY_AUTO = "by_auto"
        const val ACTION_STATE_CHANGED = "com.seyro.lite.VPN_STATE_CHANGED"
        @JvmField @Volatile var vpnState: String = "disconnected"
        @JvmField @Volatile var lastError: String = ""
        @JvmField @Volatile var lastProbeDelayMs: Long = -1L
        private const val TAG = "SEYRO-SINGBOX"
        private const val CHANNEL_ID = "seyro_vpn"
        private const val NOTIFICATION_ID = 1
        @Volatile private var activeInstance: SeyroVpnService? = null

        @JvmStatic fun requestImmediateProbe(reason: String) {
            // sing-box owns health checking; the old Xray-specific probe is intentionally removed.
            Log.d(TAG, "Probe request ignored; sing-box runtime manages transport health: $reason")
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var commandServer: CommandServer? = null
    @Volatile private var tunDescriptor: ParcelFileDescriptor? = null
    @Volatile private var currentConfig: String = ""
    private val platform: PlatformInterface by lazy { SeyroPlatformInterface(this) }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        DebugLog.i(this, "SERVICE", "VPN service onCreate; Android=${Build.VERSION.RELEASE}; SDK=${Build.VERSION.SDK_INT}; manufacturer=${Build.MANUFACTURER}; model=${Build.MODEL}")
        runCatching { Libbox.setLocale(Locale.getDefault().toLanguageTag()) }
            .onFailure {
                DebugLog.e(this, "SERVICE", "Не удалось установить locale для libbox: ${it.javaClass.simpleName}: ${it.message}")
                Log.w(TAG, "Could not set sing-box locale", it)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            worker.execute { stopRuntime(startId) }
            return START_NOT_STICKY
        }
        if (intent.action != ACTION_START) return START_NOT_STICKY
        if (vpnState == "connecting" || vpnState == "connected") return START_NOT_STICKY

        val sourceConfig = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
        if (sourceConfig.isBlank()) {
            setState("error", "Профиль пустой. Добавьте ссылку подписки.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForegroundNotification()
        } catch (e: Exception) {
            setState("error", "Не удалось запустить VPN-службу: ${e.message ?: e.javaClass.simpleName}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        setState("connecting", "")
        val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val props = active?.let { cm.getLinkProperties(it) }
        DebugLog.i(this, "SERVICE", "Получена команда запуска; transport=${intent.getStringExtra(EXTRA_TRANSPORT).orEmpty()}; configLength=${sourceConfig.length}; activeNetwork=$active; interface=${props?.interfaceName}; capabilities=$caps; validated=${caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
        worker.execute {
            try {
                DebugLog.i(this, "SERVICE", "Преобразование профиля в конфигурацию sing-box")
                val singBoxConfig = SingBoxConfigBuilder.fromXrayConfig(sourceConfig)
                currentConfig = singBoxConfig
                val root = JSONObject(singBoxConfig)
                val inbounds = root.optJSONArray("inbounds")
                val outbounds = root.optJSONArray("outbounds")
                DebugLog.i(this, "SERVICE", "Конфигурация собрана: inbounds=${inbounds?.length() ?: 0}; outbounds=${outbounds?.length() ?: 0}; dnsServers=${root.optJSONObject("dns")?.optJSONArray("servers")?.length() ?: 0}")
                initializeLibbox()
                DebugLog.i(this, "SERVICE", "Libbox инициализирован; запуск CommandServer")
                val server = CommandServer(this, platform)
                commandServer = server
                server.start()
                DebugLog.i(this, "SERVICE", "CommandServer запущен; передача конфигурации sing-box")
                server.startOrReloadService(singBoxConfig, OverrideOptions())
                Log.i(TAG, "sing-box service started")
                DebugLog.i(this, "SERVICE", "sing-box принял конфигурацию; TUN должен быть создан")
                setState("connected", "")
            } catch (e: Exception) {
                DebugLog.e(this, "SERVICE", "Ошибка запуска sing-box: ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "sing-box start failed", e)
                runCatching { commandServer?.closeService() }
                runCatching { commandServer?.close() }
                commandServer = null
                runCatching { tunDescriptor?.close() }
                tunDescriptor = null
                setState("error", "sing-box не запустился: ${e.message ?: e.javaClass.simpleName}")
                stopForegroundCompat()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeLibbox() {
        val base = filesDir
        val work = getExternalFilesDir(null) ?: filesDir
        val temp = cacheDir
        base.mkdirs(); work.mkdirs(); temp.mkdirs()
        Libbox.setup(SetupOptions().apply {
            basePath = base.absolutePath
            workingPath = work.absolutePath
            tempPath = temp.absolutePath
            fixAndroidStack = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            logMaxLines = 1000
            debug = BuildConfig.DEBUG
            crashReportSource = "SEYRO"
            appVersion = BuildConfig.VERSION_CODE.toString()
            appMarketingVersion = BuildConfig.VERSION_NAME
            platformMetadata = JSONObject().apply {
                put("os", "Android " + Build.VERSION.RELEASE)
                put("sdk", Build.VERSION.SDK_INT)
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
            }.toString()
        })
    }

    internal fun openTunInterface(options: TunOptions): Int {
        DebugLog.i(this, "TUN", "Создание TUN: mtu=${options.mtu}; autoRoute=${options.autoRoute}")
        val builder = Builder().setSession("SEYRO sing-box").setMtu(options.mtu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val v4 = options.inet4Address
        while (v4.hasNext()) {
            val address = v4.next()
            builder.addAddress(address.address(), address.prefix())
        }
        val v6 = options.inet6Address
        var hasIpv6Address = false
        while (v6.hasNext()) {
            val address = v6.next()
            builder.addAddress(address.address(), address.prefix())
            hasIpv6Address = true
        }

        if (options.autoRoute) {
            val dns = options.dnsServerAddress
            while (dns.hasNext()) builder.addDnsServer(dns.next())
            // Android's VPN builder receives the default routes; sing-box performs
            // route selection and packet processing inside its TUN inbound.
            builder.addRoute("0.0.0.0", 0)
            // Do not capture IPv6 traffic unless sing-box configured an IPv6
            // address on the TUN. A route without an IPv6 TUN address blackholes
            // IPv6-first connections and can make websites appear unavailable.
            if (hasIpv6Address && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addRoute("::", 0)
            }
        }
        runCatching { builder.addDisallowedApplication(packageName) }
        DebugLog.i(this, "TUN", "Маршруты TUN: IPv4=0.0.0.0/0; IPv6=$hasIpv6Address; DNS настроен sing-box")
        val pfd = builder.establish() ?: error("Android не создал TUN-интерфейс (VPN-разрешение отсутствует)")
        tunDescriptor?.close()
        tunDescriptor = pfd
        DebugLog.i(this, "TUN", "Android TUN создан успешно; fd=${pfd.fd}; activeNetwork=${(getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork}")
        return pfd.fd
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "SEYRO VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("SEYRO")
                .setContentText("Защищённое соединение sing-box")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentIntent(pending)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("SEYRO")
                .setContentText("Защищённое соединение sing-box")
                .setOngoing(true)
                .setContentIntent(pending)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopRuntime(startId: Int = 0) {
        setState("disconnecting", "")
        val server = commandServer
        commandServer = null
        if (server != null) {
            runCatching { server.closeService() }.onFailure { Log.w(TAG, "closeService failed", it) }
            runCatching { server.close() }.onFailure { Log.w(TAG, "CommandServer close failed", it) }
        }
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
        currentConfig = ""
        setState("disconnected", "")
        stopForegroundCompat()
        if (startId != 0) stopSelf(startId) else stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    private fun setState(state: String, error: String) {
        vpnState = state
        lastError = error
        if (error.isNotBlank()) DebugLog.e(this, "STATE", "VPN state=$state; error=$error")
        else DebugLog.i(this, "STATE", "VPN state=$state")
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    override fun onRevoke() {
        worker.execute { stopRuntime() }
        super.onRevoke()
    }

    override fun onDestroy() {
        DebugLog.w(this, "SERVICE", "VPN service onDestroy; state=$vpnState; lastError=$lastError")
        if (activeInstance === this) activeInstance = null
        worker.execute { stopRuntime() }
        worker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun serviceStop() { worker.execute { stopRuntime() } }
    override fun serviceReload() {
        val config = currentConfig
        if (config.isBlank()) return
        worker.execute {
            runCatching { commandServer?.startOrReloadService(config, OverrideOptions()) }
                .onFailure { setState("error", "Не удалось перезапустить sing-box: ${it.message}") }
        }
    }
    override fun getSystemProxyStatus(): SystemProxyStatus? = null
    override fun setSystemProxyEnabled(isEnabled: Boolean) {}
    override fun triggerNativeCrash() { throw RuntimeException("sing-box native crash requested") }
    override fun writeDebugMessage(message: String?) {
        val line = message.orEmpty().trim()
        if (line.isEmpty()) return
        Log.d(TAG, line)
        // Keep native sing-box diagnostics in the in-app log as well as Logcat,
        // so connection failures can be diagnosed without a USB/debugging setup.
        when {
            line.contains("error", ignoreCase = true) ||
                line.contains("failed", ignoreCase = true) ||
                line.contains("timeout", ignoreCase = true) ||
                line.contains("refused", ignoreCase = true) ->
                DebugLog.e(this, "SINGBOX", line.take(1200))
            else -> DebugLog.d(this, "SINGBOX", line.take(1200))
        }
    }
    override fun connectSSHAgent(): Int = -1
}
