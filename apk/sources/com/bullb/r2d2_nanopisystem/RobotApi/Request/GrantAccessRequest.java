package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class GrantAccessRequest extends BaseRequest {

    @SerializedName("uuid")
    private String UUID;

    @SerializedName("device_name")
    private String deviceName;

    public String getDeviceName() {
        return this.deviceName;
    }

    public String getUUID() {
        return this.UUID;
    }
}
