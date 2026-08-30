package com.bullb.r2d2_nanopisystem;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public class MainApplication extends Application {
    public static MainApplication instance;

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override // android.content.ContextWrapper, android.content.Context
    public Context getApplicationContext() {
        return super.getApplicationContext();
    }

    public static MainApplication getInstance() {
        return instance;
    }

    public static Context getLocalizedContext(Context context) {
        Locale locale = new Locale("fr", "");
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    @Override // android.content.ContextWrapper
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(getLocalizedContext(base));
    }
}
