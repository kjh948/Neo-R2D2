package com.bullb.r2d2_nanopisystem;

import android.content.Context;
import android.net.NetworkInfo;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.Model.EventJob.LEDJob;
import com.bullb.r2d2_nanopisystem.VoiceRecognition.VoiceRecognizer;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.bullb.r2d2_nanopisystem.utils.SharedUtils;
import java.util.Timer;
import java.util.TimerTask;

/* JADX INFO: loaded from: classes.dex */
public class LEDLightController {
    private static final int BACK_BASE_AP_MODE = 23;
    private static final int BACK_BASE_MODE_AP_CONNECTING = 24;
    private static final int BACK_BASE_MODE_LAN_WITHOUT_WIFI = 21;
    private static final int BACK_BASE_MODE_LAN_WITH_WIFI = 20;
    private static final int BACK_BASE_POWER_OFF = 22;
    private static final int BACK_BASE_SLEEP_IN_AP = 25;
    private static final int BACK_BASE_SLEEP_IN_LOCAL_NETWORK = 26;
    private static final int BACK_SPECIAL_MODE_SLEEP = 201;
    private static final int BACK_SPECIAL_MODE_VOICE_RECOGNITION = 202;
    private static final int BACK_UNCHANGE = -1;
    private static final int FRONT_BASE_BATTERY_LOW = 3;
    private static final int FRONT_BASE_MODE_PAIR = 1;
    private static final int FRONT_BASE_MODE_READY = 0;
    private static final int FRONT_BASE_POWER_OFF = 2;
    private static final int FRONT_BASE_SLEEP = 4;
    private static final int FRONT_SPECIAL_CHARGED = 6;
    private static final int FRONT_SPECIAL_CHARGING = 5;
    private static final int FRONT_SPECIAL_CONNECT_WIFI = 104;
    private static final int FRONT_SPECIAL_FACE_DETECTION = 105;
    private static final int FRONT_SPECIAL_MODE_PATROL = 102;
    private static final int FRONT_SPECIAL_PAIR_FAIL = 103;
    private static final int FRONT_UNCHANGE = -1;
    private static LEDLightController ledLightController;
    private Commander commander;
    private Context context;
    private Timer pairErrorTimer;
    private WifiService wifiService;
    private final String TAG = "LightController";
    private int frontMode = -1;
    private int backMode = -1;

    public static synchronized LEDLightController getInstance(Context context) {
        if (ledLightController == null) {
            ledLightController = new LEDLightController(context);
        }
        return ledLightController;
    }

    public LEDLightController(Context context) {
        this.commander = Commander.getInstance(context);
        this.wifiService = WifiService.getInstance(context);
        this.context = context;
    }

    public void changeToReadyLight() {
        Log.d("LightController", "ready light");
        changeLight(this.frontMode, this.backMode);
    }

    public void startPatrolLight() {
        Log.d("LightController", "start patrol");
        changeLight(102, getBackBaseMode());
    }

    public void stopPatrolLight() {
        Log.d("LightController", "Stop patrol");
        if (this.frontMode == 102) {
            changeLight(getFrontBaseMode(), -1);
        }
    }

    public void startSleepLight() {
    }

    public void failInPairMode() {
        Log.d("LightController", "Fail in pair");
        if (this.pairErrorTimer != null) {
            this.pairErrorTimer.cancel();
        }
        this.pairErrorTimer = new Timer();
        this.pairErrorTimer.schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.LEDLightController.1
            @Override // java.util.TimerTask, java.lang.Runnable
            public void run() {
                LEDLightController.this.restoreFrontBaseMode();
            }
        }, 2000L);
        if (this.frontMode != 103) {
            changeLight(103, -1);
        }
    }

    public void connectWIFIMode() {
        Log.d("LightController", "Connect Wifi Mode");
        if (this.frontMode != 104) {
            changeLight(104, -1);
        }
    }

    public void powerOffLight() {
        Log.d("LightController", "Power Off");
        changeLight(2, 22);
    }

    public LEDJob getLEDJobFromMode(int frontMode, int backMode) {
        int r;
        int b;
        int g;
        int y;
        switch (frontMode) {
            case 0:
                r = 2;
                b = 2;
                break;
            case 1:
                r = 1;
                b = 3;
                break;
            case 2:
                r = 5;
                b = 1;
                break;
            case 3:
                r = 2;
                b = 1;
                break;
            case 4:
                r = 1;
                b = 1;
                break;
            case 5:
                r = 3;
                b = 3;
                break;
            case 6:
                r = 1;
                b = 2;
                break;
            case 102:
                r = 3;
                b = 4;
                break;
            case 103:
                r = 3;
                b = 1;
                break;
            case 104:
                r = 1;
                b = 5;
                break;
            case 105:
                r = 1;
                b = 2;
                break;
            default:
                r = 0;
                b = 0;
                break;
        }
        switch (backMode) {
            case 20:
                g = 2;
                y = 1;
                break;
            case 21:
                g = 5;
                y = 1;
                break;
            case 22:
                g = 1;
                y = 1;
                break;
            case 23:
                g = 1;
                y = 2;
                break;
            case 24:
                g = 1;
                y = 5;
                break;
            case 25:
                g = 1;
                y = 3;
                break;
            case 26:
                g = 3;
                y = 1;
                break;
            case BACK_SPECIAL_MODE_SLEEP /* 201 */:
                g = 1;
                y = 1;
                break;
            case BACK_SPECIAL_MODE_VOICE_RECOGNITION /* 202 */:
                g = 3;
                y = 4;
                break;
            default:
                g = 0;
                y = 0;
                break;
        }
        return new LEDJob(r, b, y, g, 0);
    }

    public void restoreFrontBaseMode() {
        Log.d("LightController", "Restore Front");
        changeLight(getFrontBaseMode(), -1);
    }

    public void restoreBackBaseMode() {
        Log.d("LightController", "Restore Back");
        changeLight(-1, getBackBaseMode());
    }

    public void restoreAll() {
        Log.d("LightController", "Restore All");
        changeLight(getFrontBaseMode(), getBackBaseMode());
    }

    private int getBackBaseMode() {
        if (ModeController.getInstance(this.context).getMode() == 2) {
            if (this.wifiService.isAPMode()) {
                return 25;
            }
            return 26;
        }
        if (VoiceRecognizer.getInstance(this.context).isVoiceRecognitionMode()) {
            return BACK_SPECIAL_MODE_VOICE_RECOGNITION;
        }
        if (this.wifiService.isAPMode()) {
            if (this.wifiService.isAPModeConnecting()) {
                return 24;
            }
            return 23;
        }
        if (this.wifiService.getNetworkState() == NetworkInfo.DetailedState.CONNECTED) {
            return 20;
        }
        return 21;
    }

    private int getFrontBaseMode() {
        if (ModeController.getInstance(this.context).getMode() == 4) {
            return 102;
        }
        if (CentralController.getInstance(this.context).isFaceDetected()) {
            return 105;
        }
        ModeController modeController = ModeController.getInstance(this.context);
        if (modeController.getMode() == 3) {
            return modeController.isConnectingWifiInPairMode() ? 104 : 1;
        }
        if (RobotPreference.getRobotCharging(this.context) == 1) {
            return 5;
        }
        if (RobotPreference.getRobotCharging(this.context) == 2) {
            return 6;
        }
        if (RobotPreference.getRobotBattery(this.context) <= SharedUtils.LOW_BATTERY_PERCENTAGE) {
            return 3;
        }
        return ModeController.getInstance(this.context).getMode() == 2 ? 4 : 0;
    }

    public void changeLight(int fMode, int bMode) {
        if (this.backMode == 22 || this.frontMode == 2) {
            Log.d("LightController", "Power off light start, cannot change light anymore");
            return;
        }
        if (this.frontMode == fMode) {
            fMode = -1;
        }
        if (this.backMode == bMode) {
            bMode = -1;
        }
        Log.d("LightController", "Change Front Light:" + String.valueOf(this.frontMode) + "->" + String.valueOf(fMode));
        Log.d("LightController", "Change Back Light:" + String.valueOf(this.backMode) + "->" + String.valueOf(bMode));
        if (fMode != -1) {
            this.frontMode = fMode;
        }
        if (bMode != -1) {
            this.backMode = bMode;
        }
        if (fMode != -1 || bMode != -1) {
            Log.d("LightController", "Now Front:" + String.valueOf(this.frontMode));
            Log.d("LightController", "Now Back:" + String.valueOf(this.backMode));
            LEDJob ledJob = getLEDJobFromMode(fMode, bMode);
            this.commander.LED(ledJob.getR(), ledJob.getB(), ledJob.getY(), ledJob.getG());
        }
    }

    public void faceDetectLightStart() {
        changeLight(105, -1);
    }

    public void faceDetectLightStop() {
        restoreFrontBaseMode();
    }

    public void chargingLightStart() {
        changeLight(5, -1);
    }

    public void chargedLightStart() {
        changeLight(6, -1);
    }

    public void chargingLightStop() {
        restoreFrontBaseMode();
    }
}
