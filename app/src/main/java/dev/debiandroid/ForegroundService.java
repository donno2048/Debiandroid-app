package dev.debiandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ForegroundService extends Service {
    private static final String CHANNEL = "debiandroid";

    @Override
    public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "Debiandroid", NotificationManager.IMPORTANCE_NONE)
        );
        startForeground(1, new Notification.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}
