package com.bullb.r2d2_nanopisystem;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import java.lang.Thread;

/* JADX INFO: loaded from: classes.dex */
public class MyExceptionHandler implements Thread.UncaughtExceptionHandler {
    private Activity activity;

    public MyExceptionHandler(Activity a) {
        this.activity = a;
    }

    @Override // java.lang.Thread.UncaughtExceptionHandler
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e("MyExceptionHandler", "error caught", ex);
        ex.printStackTrace();
        Intent intent = new Intent(this.activity, (Class<?>) MainActivity.class);
        intent.putExtra("crash", true);
        intent.addFlags(335577088);
        PendingIntent pendingIntent = PendingIntent.getActivity(MainApplication.getInstance().getBaseContext(), 0, intent, 1073741824);
        AlarmManager mgr = (AlarmManager) MainApplication.getInstance().getBaseContext().getSystemService("alarm");
        mgr.set(1, System.currentTimeMillis() + 100, pendingIntent);
        this.activity.finish();
        System.exit(2);
    }
}
