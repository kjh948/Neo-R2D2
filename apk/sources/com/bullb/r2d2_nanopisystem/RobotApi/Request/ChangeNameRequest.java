package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class ChangeNameRequest extends BaseRequest {

    @SerializedName("new_name")
    private String newName;

    public String getNewName() {
        return this.newName;
    }
}
