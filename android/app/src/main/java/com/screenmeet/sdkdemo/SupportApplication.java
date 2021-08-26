package com.screenmeet.sdkdemo;

import android.app.Application;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.screenmeet.sdk.ScreenMeet;
import com.screenmeet.sdkdemo.overlay.WidgetManager;
import com.screenmeet.sdkdemo.service.ForegroundServiceConnection;

public class SupportApplication extends Application {

    static SupportApplication instance = null;
    WidgetManager widget = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        widget = new WidgetManager(this);

        //TODO Provide your API token below
        ScreenMeet.Configuration configuration = new ScreenMeet.Configuration(BuildConfig.SM_API_KEY);
        configuration.logLevel(ScreenMeet.Configuration.LogLevel.VERBOSE);

        ScreenMeet.init(this, configuration);
        registerActivityLifecycleCallbacks(ScreenMeet.activityLifecycleCallback());
    }

    private static final ForegroundServiceConnection serviceConnection =
            new ForegroundServiceConnection();

    private static final LifecycleObserver observer = new LifecycleObserver() {
        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        public void onMoveToForeground() {
            if (instance != null) {
                serviceConnection.bind(instance);
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        public void onMoveToBackground() {
            if (instance != null) {
                serviceConnection.unbind(instance);
            }
        }
    };

    static void startListeningForeground(){
        ProcessLifecycleOwner
                .get().getLifecycle()
                .addObserver(observer);
    }

    static void stopListeningForeground(){
        ProcessLifecycleOwner
                .get().getLifecycle()
                .removeObserver(observer);
    }

}