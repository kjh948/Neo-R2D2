package com.bullb.r2d2_nanopisystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* JADX INFO: loaded from: classes.dex */
public class StartAppAtBootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            intent.setClass(context, MainActivity.class);
            intent.addFlags(268435456);
            context.startActivity(intent);
        }
    }
}
