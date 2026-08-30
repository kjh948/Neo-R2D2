package com.bullb.r2d2_nanopisystem.Model;

import android.content.Context;
import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.google.gson.annotations.SerializedName;

/* JADX INFO: loaded from: classes.dex */
public class Robot {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_PATROL = 4;

    @SerializedName(Commander.ARM)
    private boolean arm;

    @SerializedName("battery")
    private int battery;

    @SerializedName("charging")
    private int charging;

    @SerializedName("error")
    private String error;

    @SerializedName("ip")
    private String ip;

    @SerializedName("ap_mode")
    private boolean isAPMode;

    @SerializedName(RobotApiHandler.FACE_DETECTION)
    private boolean isFaceDetectionEnabled;

    @SerializedName(RobotApiHandler.MUTE)
    private boolean isMuted;

    @SerializedName(RobotApiHandler.VOICE_RECOGNITION)
    private boolean isVoiceRecognitionEnabled;

    @SerializedName(Commander.LIGHTSABER)
    private boolean lightsaber;

    @SerializedName("lcd_l")
    private boolean longLCD;

    @SerializedName(Commander.MODE)
    private int mode;

    @SerializedName("name")
    private String name;

    @SerializedName(Commander.PROJECTOR)
    private int projector;

    @SerializedName("self_update")
    private int selfUpdate;

    @SerializedName("lcd_s")
    private boolean shortLCD;

    @SerializedName("ssid")
    private String ssid;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("update_dl_progress")
    private int updateDownloadProgress;

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("version")
    private int version;

    public Robot(Context context, boolean currentRobot) {
        if (currentRobot) {
            Long tsLong = Long.valueOf(System.currentTimeMillis());
            this.name = RobotPreference.getRobotName(context);
            this.uuid = RobotPreference.getRobotUdid(context);
            this.isFaceDetectionEnabled = RobotPreference.isEnabledFaceDetection(context);
            this.isVoiceRecognitionEnabled = RobotPreference.isEnabledVoiceRecognition(context);
            this.isMuted = RobotPreference.isEnabledMute(context);
            this.ip = WifiService.getInstance(context).getIPAddress();
            this.battery = RobotPreference.getRobotBattery(context);
            this.charging = RobotPreference.getRobotCharging(context);
            this.lightsaber = RobotPreference.getLightsaber(context);
            this.arm = RobotPreference.getRobotArm(context);
            this.projector = RobotPreference.getRobotProjector(context);
            this.timestamp = tsLong.longValue();
            this.mode = ModeController.getInstance(context).getMode();
            this.shortLCD = RobotPreference.getRobotShortLCD(context);
            this.longLCD = RobotPreference.getRobotLongLCD(context);
            this.isAPMode = WifiService.getInstance(context).isAPMode();
            this.version = 15;
            this.ssid = WifiService.getInstance(context).getCurrentNetworkSSIDCut();
            this.selfUpdate = RobotPreference.getRobotSelfUpdate(context);
            this.updateDownloadProgress = RobotPreference.getRobotUpdateDownloadProgress(context);
            this.error = RobotPreference.getRobotError(context);
        }
    }

    public String getName() {
        return this.name;
    }
}
