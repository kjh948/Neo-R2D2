package com.bullb.r2d2_nanopisystem.utils;

import android.content.Context;
import android.content.SharedPreferences;

/* JADX INFO: loaded from: classes.dex */
public class WIFIPreference {
    public static String WIFI_PREFERENCE_NAME = "wifi";
    public static String WIFI_SSID = "wifiSSID";
    public static String WIFI_PASSWORD = "wifi_password";
    public static String IS_AP_MODE = "is_ap_mode";

    public static void setWifiSSID(Context context, String bluetoothName) {
        SharedPreferences prefs = context.getSharedPreferences(WIFI_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(WIFI_SSID, bluetoothName);
        e.commit();
    }

    public static String getWifiSID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(WIFI_PREFERENCE_NAME, 0);
        String udid = prefs.getString(WIFI_SSID, null);
        return udid;
    }

    public static void setWifiPassword(Context context, String bluetoothName) {
        SharedPreferences prefs = context.getSharedPreferences(WIFI_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(WIFI_PASSWORD, bluetoothName);
        e.commit();
    }

    public static String getWifiPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(WIFI_PREFERENCE_NAME, 0);
        String udid = prefs.getString(WIFI_PASSWORD, null);
        return udid;
    }

    public static void setIsAPMode(Context context, boolean isAPMode) {
        SharedPreferences prefs = context.getSharedPreferences(WIFI_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(IS_AP_MODE, isAPMode);
        e.commit();
    }

    public static boolean isAPMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(WIFI_PREFERENCE_NAME, 0);
        boolean isAPMode = prefs.getBoolean(IS_AP_MODE, true);
        return isAPMode;
    }

    public static void clearPreference(Context context) {
        setWifiSSID(context, null);
        setWifiPassword(context, null);
        setIsAPMode(context, true);
    }
}
