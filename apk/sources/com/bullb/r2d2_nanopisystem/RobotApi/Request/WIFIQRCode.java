package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class WIFIQRCode extends BaseRequest {

    @SerializedName("key")
    private String key;

    @SerializedName("pw")
    private String pw;

    @SerializedName("ssid")
    private String ssid;

    public String getSSID() {
        return this.ssid;
    }

    public String getPw() {
        return this.pw;
    }

    public String getKey() {
        return this.key;
    }
}
