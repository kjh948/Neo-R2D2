package com.bullb.r2d2_nanopisystem.RobotApi.Request;

import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class AssignRobotInfoRequest extends BaseRequest {

    @SerializedName("api_token")
    public String apiToken;

    @SerializedName("robot_access_key")
    public String robotAccessKey;

    @SerializedName("udid")
    public String udid;
}
