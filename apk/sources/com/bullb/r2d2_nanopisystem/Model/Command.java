package com.bullb.r2d2_nanopisystem.Model;

import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class Command {

    @SerializedName("angle")
    public int angle;

    @SerializedName("cmd")
    public String cmd;

    @SerializedName("dir")
    public int dir;

    @SerializedName("interrupt")
    public int interrupt;

    @SerializedName(Commander.MODE)
    public int mode;

    @SerializedName(RobotApiHandler.POWER)
    public int power;

    @SerializedName("sound_id")
    public int sound_id;

    @SerializedName("url")
    public String url;

    @SerializedName("value")
    public int value;

    @SerializedName("r")
    public int r = -1;

    @SerializedName("b")
    public int b = -1;

    @SerializedName("y")
    public int y = -1;

    @SerializedName("g")
    public int g = -1;

    @SerializedName("s")
    public int s = -1;

    @SerializedName("l")
    public int l = -1;
}
