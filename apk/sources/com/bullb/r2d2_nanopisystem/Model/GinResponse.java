package com.bullb.r2d2_nanopisystem.Model;

import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class GinResponse {

    @SerializedName(Commander.ARM)
    public int arm;

    @SerializedName("batt")
    public int batt;

    @SerializedName("charging-status")
    public int chargingStatus;

    @SerializedName("cmd")
    public String cmd;

    @SerializedName("error")
    private String error;

    @SerializedName("head")
    public int head;

    @SerializedName("lcd_l")
    private int lcd_l;

    @SerializedName("lcd_s")
    private int lcd_s;

    @SerializedName(Commander.LIGHTSABER)
    public int lightsaber;

    @SerializedName(Commander.MODE)
    public int mode;

    @SerializedName(Commander.PROJECTOR)
    public int projector;

    @SerializedName("status")
    public int status;

    public boolean getLcd_s() {
        return this.lcd_s >= 2;
    }

    public boolean getLCD_l() {
        return this.lcd_l >= 2;
    }

    public String getError() {
        return this.error == null ? RobotPreference.NO_ERROR : this.error;
    }
}
