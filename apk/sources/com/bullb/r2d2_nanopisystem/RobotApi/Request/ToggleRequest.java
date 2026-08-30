package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class ToggleRequest extends BaseRequest {

    @SerializedName("enable")
    private boolean enable;

    public boolean isEnable() {
        return this.enable;
    }
}
