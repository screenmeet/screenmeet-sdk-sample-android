package com.screenmeet.sdkdemo.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class ForegroundServiceConnection implements ServiceConnection {

    private boolean bound = false;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        bound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bound = false;
    }

    public void bind(Context context){
        context.startService(new Intent(context, ForegroundService.class));
        context.bindService(new Intent(context, ForegroundService.class),
                this,
                Context.BIND_AUTO_CREATE
        );
    }

    public void unbind(Context context){
        if (bound) {
            context.unbindService(this);
            bound = false;
        }
    }
}
