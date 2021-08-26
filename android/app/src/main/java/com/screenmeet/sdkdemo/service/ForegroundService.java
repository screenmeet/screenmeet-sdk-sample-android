package com.screenmeet.sdkdemo.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.screenmeet.sdkdemo.MainActivity;
import com.screenmeet.sdkdemo.R;

public class ForegroundService extends Service {

    private static final String ANDROID_CHANNEL_ID = "Live Main";
    private static final int NOTIFICATION_ID = 101;

    private static final String TAG = ForegroundService.class.getSimpleName();
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = "started_from_notification";

    private final IBinder binder = new LocalBinder();

    private Handler serviceHandler;

    private boolean configurationChanged = false;

    public ForegroundService() { }

    @Override
    public void onCreate() {
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        serviceHandler = new Handler(handlerThread.getLooper());
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel channel = new NotificationChannel(
                            ANDROID_CHANNEL_ID,
                            name,
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false);

        if (startedFromNotification) {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configurationChanged = true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        stopForeground(true);
        configurationChanged = false;
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        stopForeground(true);
        configurationChanged = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!configurationChanged) {
            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true;
    }

    @Override
    public void onDestroy() {
        serviceHandler.removeCallbacksAndMessages(null);
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ANDROID_CHANNEL_ID)
                .setContentIntent(activityPendingIntent)
                .setContentText("Live session ongoing...")
                .setContentTitle("ScreenMeet Sample Application")
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_support_agent)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(ANDROID_CHANNEL_ID);
        }

        return builder.build();
    }

    public class LocalBinder extends Binder {
        ForegroundService getService() {
            return ForegroundService.this;
        }
    }

    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }
}