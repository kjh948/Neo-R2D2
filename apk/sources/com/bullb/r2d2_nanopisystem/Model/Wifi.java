package com.bullb.r2d2_nanopisystem.Model;

import android.support.annotation.NonNull;
import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class Wifi implements Comparable<Wifi> {

    @SerializedName("rssi")
    private int rssi;

    @SerializedName("ssid")
    private String ssid;

    public Wifi(String ssid, int rssi) {
        this.ssid = ssid;
        this.rssi = rssi;
    }

    @Override // java.lang.Comparable
    public int compareTo(@NonNull Wifi another) {
        return Integer.valueOf(another.rssi).compareTo(Integer.valueOf(this.rssi));
    }
}
