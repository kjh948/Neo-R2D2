package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class ConnectWifiRequest extends BaseRequest {

    @SerializedName("wifi_pw")
    private String password;

    @SerializedName("ssid")
    private String ssid;

    public String getSSID() {
        return this.ssid;
    }

    public String getPassword() {
        return this.password;
    }
}
