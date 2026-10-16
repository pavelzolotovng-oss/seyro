package com.seyro.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import hev.htproxy.TProxyService;
import android.util.Log;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;
import go.Seq;

public class SeyroVpnService extends VpnService {
    public static final String ACTION_START = "com.seyro.lite.START_VPN";
    public static final String ACTION_STOP = "com.seyro.lite.STOP_VPN";
    public static final String EXTRA_CONFIG = "xray_config";
    private static final String TAG = "SEYRO-VPN";
    private static final String CHANNEL_ID = "seyro_vpn";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STATE_CHANGED = "com.seyro.lite.VPN_STATE_CHANGED";
    public static volatile String vpnState = "disconnected";
    public static volatile String lastError = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService vpnExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService diagnosticsExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService probeExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> diagnosticsTask;
    private ScheduledFuture<?> probeTask;
    private int consecutiveProbeSuccesses = 0;
    private long nextProbeIntervalSeconds = 60;
    private final Map<String, Long> previousXrayCounters = new HashMap<>();
    private long[] previousHevStats;
    private ParcelFileDescriptor tun;
    private CoreController controller;
    private boolean hevStarted = false;
    private File hevConfigFile;

    private final CoreCallbackHandler callback = new CoreCallbackHandler() {
        @Override public long startup() { DebugLog.i(SeyroVpnService.this,"XRAY","Core callback startup"); return 0L; }
        @Override public long shutdown() { DebugLog.i(SeyroVpnService.this,"XRAY","Core callback shutdown"); return 0L; }
        @Override public long onEmitStatus(long level, String message) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Xray[" + level + "]: " + message);
            DebugLog.d(SeyroVpnService.this,"XRAY","level="+level+" "+message);
            return 0L;
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        DebugLog.i(this,"SERVICE","onStartCommand action="+(intent==null?"null":intent.getAction()));
        if(intent==null || ACTION_STOP.equals(intent.getAction())) {
            setVpnState("disconnecting","");
            vpnExecutor.execute(() -> {
                stopVpn();
                setVpnState("disconnected","");
                DebugLog.i(this,"VPN","Отключение завершено");
                mainHandler.post(() -> { stopForeground(true); stopSelf(startId); });
            });
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        String config=intent.getStringExtra(EXTRA_CONFIG);
        if(config==null || config.trim().isEmpty()) {
            setVpnState("error","Профиль пустой. Добавьте ссылку и попробуйте снова.");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if("connecting".equals(vpnState)||"connected".equals(vpnState)) return START_NOT_STICKY;
        try {
            startInForeground();
        } catch(Exception e) {
            Log.e(TAG,"Could not start foreground notification",e);
            setVpnState("error","Не удалось запустить VPN-службу: "+safeMessage(e));
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        DebugLog.i(this,"VPN","Начинается запуск ядра Xray");
        setVpnState("connecting","");
        vpnExecutor.execute(() -> {
            try {
                startVpn(config);
                DebugLog.i(this,"VPN","Xray запущен; интерфейс создан; проверка доступности интернета ещё не выполнена");
                setVpnState("connected","");
            } catch(Exception e) {
                Log.e(TAG,"VPN start failed",e);
                DebugLog.e(this,"VPN","Ошибка запуска: "+e.getClass().getSimpleName()+": "+safeMessage(e));
                stopVpn();
                setVpnState("error","Xray не запустился: "+safeMessage(e));
                mainHandler.post(() -> { stopForeground(true); stopSelf(startId); });
            }
        });
        return START_NOT_STICKY;
    }

    private String safeMessage(Exception error) {
        String message=error.getMessage();
        return message==null||message.trim().isEmpty()?error.getClass().getSimpleName():message;
    }

    private void setVpnState(String state,String error) {
        DebugLog.i(this,"STATE","VPN state="+state+(error==null||error.isEmpty()?"":"; error="+error));
        vpnState=state;
        lastError=error;
        Intent broadcast=new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName());
        sendBroadcast(broadcast);
    }

    private void startInForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "SEYRO VPN", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Статус VPN-подключения SEYRO");
            manager.createNotificationChannel(channel);
        }
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("SEYRO Lite")
            .setContentText("Защищённое соединение SEYRO")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startVpn(String config) throws Exception {
        DebugLog.i(this,"VPN","Подготовка TUN-интерфейса");
        stopVpn();
        Builder builder = new Builder();
        builder.setSession("SEYRO Lite")
            .setMtu(1400)
            .addAddress("10.77.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("9.9.9.9");
        // Keep this app's own Xray sockets outside its VPN to prevent a routing loop.
        builder.addDisallowedApplication(getPackageName());
        tun = builder.establish();
        if (tun == null) throw new IllegalStateException("Android did not establish the VPN interface");
        DebugLog.i(this,"VPN","TUN-интерфейс создан; fd доступен; MTU=1400; IPv4 default route и DNS заданы");
        Seq.setContext(getApplicationContext());
        // AndroidLibXrayLite requires an XUDP BaseKey of exactly 32 bytes.
        Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY");
        controller = Libv2ray.newCoreController(callback);
        // Xray exposes a loopback SOCKS inbound. HEV consumes the Android TUN fd and
        // forwards TCP/UDP packets to that SOCKS inbound (tun2socks architecture).
        DebugLog.i(this,"XRAY","Запуск Xray с локальным SOCKS inbound 127.0.0.1:10808; TUN fd обслуживает HEV");
        controller.startLoop(config, 0); // 0 tells AndroidLibXrayLite not to attach the fd to Xray TUN.
        DebugLog.i(this,"XRAY","StartLoop вернулся; isRunning="+controller.getIsRunning());
        if (!controller.getIsRunning()) throw new IllegalStateException("Xray core did not start");
        waitForLocalSocks(8000);
        startHevTunnel();
        startTrafficDiagnostics();
        Log.i(TAG, "VPN interface and Xray core started");
    }

    private void waitForLocalSocks(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", 10808), 400);
                DebugLog.i(this, "SOCKS", "Локальный SOCKS inbound доступен на 127.0.0.1:10808");
                return;
            } catch (Exception e) {
                last = e;
                Thread.sleep(150);
            }
        }
        throw new IllegalStateException("Xray SOCKS inbound 127.0.0.1:10808 недоступен", last);
    }

    private void startHevTunnel() throws Exception {
        // Loading this class loads the self-contained HEV AAR native library.
        String yaml = "tunnel:\n"
            + "  name: seyro\n"
            + "  mtu: 1400\n"
            + "  ipv4: 10.77.0.1\n"
            + "socks5:\n"
            + "  address: 127.0.0.1\n"
            + "  port: 10808\n"
            + "  udp: 'udp'\n";
        hevConfigFile = new File(getFilesDir(), "hev-socks5-tunnel.yml");
        try (FileOutputStream out = new FileOutputStream(hevConfigFile, false)) {
            out.write(yaml.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        DebugLog.i(this, "HEV", "Запуск hev-socks5-tunnel; TUN fd=" + tun.getFd() + "; SOCKS=127.0.0.1:10808");
        boolean started = TProxyService.TProxyStartService(hevConfigFile.getAbsolutePath(), tun.getFd());
        if (!started) throw new IllegalStateException("hev-socks5-tunnel не запустился (TProxyStartService=false)");
        hevStarted = true;
        if (!TProxyService.TProxyIsRunning()) throw new IllegalStateException("hev-socks5-tunnel не подтвердил запуск");
        DebugLog.i(this, "HEV", "hev-socks5-tunnel запущен; TUN пакеты направляются в Xray SOCKS");
    }

    private void startTrafficDiagnostics() {
        if (diagnosticsTask != null) diagnosticsTask.cancel(true);
        if (probeTask != null) probeTask.cancel(true);
        previousXrayCounters.clear(); previousHevStats = null;
        consecutiveProbeSuccesses = 0; nextProbeIntervalSeconds = 60;
        diagnosticsTask = diagnosticsExecutor.scheduleWithFixedDelay(() -> {
            CoreController current = controller;
            if (current == null || !current.getIsRunning()) return;
            try {
                String traffic = current.queryAllOutboundTrafficStats();
                if (traffic != null && !traffic.trim().isEmpty()) logXrayTrafficDelta(traffic);
                if (hevStarted && TProxyService.TProxyIsRunning()) {
                    long[] stats = TProxyService.TProxyGetStats();
                    if (stats != null && stats.length >= 4) {
                        long[] delta = new long[4]; boolean hasDelta = previousHevStats != null;
                        for (int i=0;i<4;i++) { delta[i] = previousHevStats == null || stats[i] < previousHevStats[i] ? 0 : stats[i]-previousHevStats[i]; if(delta[i]!=0) hasDelta=true; }
                        previousHevStats = new long[]{stats[0],stats[1],stats[2],stats[3]};
                        if(hasDelta) DebugLog.d(this,"HEV_STATS","за 30с: txPackets=+"+delta[0]+"; txBytes=+"+delta[1]+"; rxPackets=+"+delta[2]+"; rxBytes=+"+delta[3]);
                    }
                }
            } catch(Exception e) { DebugLog.w(this,"TRAFFIC","counter read failed: "+e.getClass().getSimpleName()); }
        },30,30,TimeUnit.SECONDS);
        scheduleNextProbe(5);
    }

    private void logXrayTrafficDelta(String traffic) {
        Matcher m=Pattern.compile("(?i)(uplink|downlink|upload|download)\\\\s*[:=]\\\\s*(\\\\d+)").matcher(traffic);
        StringBuilder out=new StringBuilder();
        while(m.find()) {
            String key=m.group(1).toLowerCase(java.util.Locale.ROOT); long value;
            try { value=Long.parseLong(m.group(2)); } catch(NumberFormatException ex) { continue; }
            Long previous=previousXrayCounters.put(key,value);
            if(previous!=null) { long delta=value>=previous?value-previous:value; if(delta>0) { if(out.length()>0) out.append("; "); out.append(key).append("=+").append(delta); } }
        }
        if(out.length()>0) DebugLog.d(this,"TRAFFIC","за 30с: "+out);
    }

    private void scheduleNextProbe(long delaySeconds) {
        if(probeTask!=null) probeTask.cancel(false);
        probeTask=probeExecutor.schedule(() -> {
            CoreController current=controller;
            if(current==null || !current.getIsRunning()) { scheduleNextProbe(60); return; }
            long started=System.currentTimeMillis();
            try {
                long delay=current.measureDelay("https://www.google.com/generate_204"); long elapsed=System.currentTimeMillis()-started;
                consecutiveProbeSuccesses++;
                if(consecutiveProbeSuccesses>=3) nextProbeIntervalSeconds=Math.min(300,nextProbeIntervalSeconds+60);
                DebugLog.d(this,"PROBE","Проверка успешна: delay="+delay+" ms; elapsed="+elapsed+" ms; следующий интервал="+nextProbeIntervalSeconds+" s");
                scheduleNextProbe(nextProbeIntervalSeconds);
            } catch(Exception e) {
                consecutiveProbeSuccesses=0; nextProbeIntervalSeconds=30;
                String detail=e.getMessage(); if(detail==null || detail.trim().isEmpty()) detail=e.toString();
                detail=detail.replaceAll("(?i)vless://[^\\\\s]+","vless://[redacted]"); if(detail.length()>240) detail=detail.substring(0,240);
                DebugLog.w(this,"PROBE","Проверка не удалась: "+e.getClass().getSimpleName()+"; detail="+detail+"; elapsed="+(System.currentTimeMillis()-started)+" ms; повтор через 30 s");
                scheduleNextProbe(30);
            }
        },delaySeconds,TimeUnit.SECONDS);
    }
    private void stopVpn() {
        if (diagnosticsTask != null) { diagnosticsTask.cancel(true); diagnosticsTask = null; }
        if (probeTask != null) { probeTask.cancel(true); probeTask = null; }
        DebugLog.i(this,"VPN","Остановка HEV/Xray и закрытие TUN");
        if (hevStarted) {
            try {
                boolean stopped = TProxyService.TProxyStopService();
                DebugLog.i(this, "HEV", "Остановка hev-socks5-tunnel; result=" + stopped);
            } catch (Throwable e) {
                DebugLog.w(this, "HEV", "Ошибка остановки HEV: " + e.getClass().getSimpleName());
            }
            hevStarted = false;
        }
        try {
            if (controller != null) controller.stopLoop();
        } catch (Exception e) {
            Log.w(TAG, "Error stopping Xray", e);
        }
        controller = null;
        try { if (tun != null) tun.close(); } catch (Exception ignored) {}
        tun = null;
    }

    @Override public void onRevoke() {
        DebugLog.w(this,"VPN","Android отозвал VPN-разрешение");
        setVpnState("disconnected","");
        stopVpn();
        stopForeground(true);
        stopSelf();
        super.onRevoke();
    }

    @Override public void onDestroy() {
        DebugLog.i(this,"SERVICE","Служба уничтожается");
        vpnExecutor.shutdownNow();
        diagnosticsExecutor.shutdownNow();
        probeExecutor.shutdownNow();
        stopVpn();
        stopForeground(true);
        super.onDestroy();
    }
}
