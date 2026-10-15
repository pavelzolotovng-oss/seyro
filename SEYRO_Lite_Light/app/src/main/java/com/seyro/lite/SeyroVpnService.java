package com.seyro.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
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
    private ParcelFileDescriptor tun;
    private CoreController controller;

    private final CoreCallbackHandler callback = new CoreCallbackHandler() {
        @Override public long startup() { return 0L; }
        @Override public long shutdown() { return 0L; }
        @Override public long onEmitStatus(long level, String message) {
            Log.i(TAG, "Xray[" + level + "]: " + message);
            return 0L;
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        String config = intent.getStringExtra(EXTRA_CONFIG);
        if (config == null || config.trim().isEmpty()) {
            Log.e(TAG, "Missing Xray configuration");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startInForeground();
            startVpn(config);
            return START_NOT_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "VPN start failed", e);
            stopVpn();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
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
            .setContentText("VPN-подключение активно")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void startVpn(String config) throws Exception {
        stopVpn();
        Builder builder = new Builder();
        builder.setSession("SEYRO Lite")
            .setMtu(1500)
            .addAddress("10.77.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("9.9.9.9");
        tun = builder.establish();
        if (tun == null) throw new IllegalStateException("Android did not establish the VPN interface");
        Seq.setContext(getApplicationContext());
        Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "seyro-lite");
        controller = Libv2ray.newCoreController(callback);
        controller.startLoop(config, tun.getFd());
        if (!controller.getIsRunning()) throw new IllegalStateException("Xray core did not start");
        Log.i(TAG, "VPN interface and Xray core started");
    }

    private void stopVpn() {
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
        stopVpn();
        stopForeground(true);
        stopSelf();
        super.onRevoke();
    }

    @Override public void onDestroy() {
        stopVpn();
        stopForeground(true);
        super.onDestroy();
    }
}
