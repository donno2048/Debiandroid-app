package dev.debiandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;

public class ForegroundService extends Service {
    private static final String CHANNEL = "debiandroid";
    private static WakeLock wakeLock;
    private static WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "Debiandroid", NotificationManager.IMPORTANCE_NONE)
        );
        startForeground(1, new Notification.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).build());
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Debiandroid:WakeLock");
        wakeLock.acquire();
        wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Debiandroid:WakeLock");
        wifiLock.acquire();
        Intent igroneOptimizations = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        igroneOptimizations.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(igroneOptimizations);
    }

    @Override
    public void onDestroy() {
        wakeLock.release();
        wifiLock.release();
        super.onDestroy();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}
