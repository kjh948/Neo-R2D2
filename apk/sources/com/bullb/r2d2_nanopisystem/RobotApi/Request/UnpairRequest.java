package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class UnpairRequest extends BaseRequest {

    @SerializedName("uuid")
    private String uuid;

    public String getUUID() {
        return this.uuid;
    }
}
