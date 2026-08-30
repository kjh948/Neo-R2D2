package com.bullb.r2d2_nanopisystem.ModeControl;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CentralController;
import com.bullb.r2d2_nanopisystem.ModeControl.PairModeController;
import com.bullb.r2d2_nanopisystem.ModeControl.PatrolController;
import com.bullb.r2d2_nanopisystem.ModeControl.SleepController;
import com.bullb.r2d2_nanopisystem.WebSocket.SocketServer;
import com.bullb.r2d2_nanopisystem.utils.SharedUtils;
import java.net.UnknownHostException;

/* JADX INFO: loaded from: classes.dex */
public class ModeController implements PairModeController.PairStateChangeListener, SleepController.SleepStateChangeListener, PatrolController.PatrolStateChangeListener {
    public static final int MODE_PAIR = 3;
    public static final int MODE_PATROL = 4;
    public static final int MODE_READY = 1;
    public static final int MODE_SLEEP = 2;
    public static final int MODE_USER_CONTROL = 5;
    private static ModeController modeController;
    private Context context;
    private PairModeController pairModeController;
    private PatrolController patrolController;
    private SleepController sleepController;
    public final String TAG = "ModeController";
    private int currentMode = 1;

    public static synchronized ModeController getInstance(Context context) {
        if (modeController == null) {
            modeController = new ModeController(context);
        }
        return modeController;
    }

    private ModeController(Context context) {
        this.context = context;
        this.pairModeController = new PairModeController(context, this);
        this.sleepController = new SleepController(context, this);
        this.patrolController = new PatrolController(context, this);
    }

    public int getMode() {
        return this.currentMode;
    }

    public void setMode(int mode) {
        Log.d("ModeController", "setMode(" + mode + ")");
        this.currentMode = mode;
        SharedUtils.notifyRobotChanged(this.context);
    }

    public boolean startPairMode() {
        Log.i("ModeController", "startPairMode()");
        if (this.pairModeController.isProcessing()) {
            Log.d("ModeController", "drop start pair");
            return false;
        }
        this.pairModeController.startPairMode();
        return true;
    }

    public boolean stopPairMode() {
        Log.i("ModeController", "stopPairMode()");
        if (this.pairModeController.isProcessing()) {
            Log.d("ModeController", "drop stop pair");
            return false;
        }
        this.pairModeController.stopPairMode();
        return true;
    }

    @Nullable
    public String getPairKey() {
        if (this.pairModeController == null) {
            return null;
        }
        return this.pairModeController.getPairKey();
    }

    public void successConnectionInPairMode() {
        this.pairModeController.successConnectionEstablished();
    }

    public boolean isConnectingWifiInPairMode() {
        return this.pairModeController.isConnectingWifi();
    }

    public void processQRCodeInPairMode(String text) {
        this.pairModeController.processQRCode(text);
    }

    @Override // com.bullb.r2d2_nanopisystem.ModeControl.PairModeController.PairStateChangeListener
    public void onPairStart() {
        if (this.currentMode == 4) {
            stopPatrolMode();
        }
        setMode(3);
        stopSleepTimer();
        Log.d("ModeController", "Mode:" + String.valueOf(this.currentMode));
    }

    @Override // com.bullb.r2d2_nanopisystem.ModeControl.PairModeController.PairStateChangeListener
    public void onPairStop() {
        resetMode(false);
        restartSleepTimer();
        Log.d("ModeController", "Mode:" + String.valueOf(this.currentMode));
    }

    public void wake() {
        this.sleepController.wake();
    }

    @Override // com.bullb.r2d2_nanopisystem.ModeControl.SleepController.SleepStateChangeListener
    public void onSleep() {
        setMode(2);
        Log.d("ModeController", "Mode:" + String.valueOf(this.currentMode));
    }

    @Override // com.bullb.r2d2_nanopisystem.ModeControl.SleepController.SleepStateChangeListener
    public void onWake() {
        setMode(1);
        Log.d("ModeController", "Mode:" + String.valueOf(this.currentMode));
    }

    public void startPatrolMode() {
        Log.d("ModeController", "start Patrol");
        if (this.currentMode == 3) {
            stopPairMode();
        }
        stopSleepTimer();
        this.patrolController.startPatrol();
    }

    public void stopPatrolMode() {
        Log.d("ModeController", "stop Patrol");
        restartSleepTimer();
        this.patrolController.stopPatrol();
    }

    @Override // com.bullb.r2d2_nanopisystem.ModeControl.PatrolController.PatrolStateChangeListener
    public void onPatrolStart() {
        setMode(4);
        Log.d("ModeController", "Mode:" + String.valueOf(this.currentMode));
    }

    @Override // com.bullb.r2d2_nanopisystem.ModeControl.PatrolController.PatrolStateChangeListener
    public void onPatrolStop() {
        resetMode(true);
        Log.d("ModeController", "Mode:" + String.valueOf(this.currentMode));
    }

    private void resetMode(boolean stopPatrol) {
        try {
            if (SocketServer.getInstance(this.context).getControllingNum() > 0) {
                if (stopPatrol && this.currentMode == 4) {
                    stopSleepTimer();
                    startUserControl();
                } else {
                    startUserControlMode();
                }
            } else {
                setMode(1);
            }
        } catch (UnknownHostException e) {
            setMode(1);
            e.printStackTrace();
        }
    }

    public void restartSleepTimer() {
        this.sleepController.restartSleepTimer();
    }

    public void stopSleepTimer() {
        this.sleepController.stopTimer();
    }

    public void startUserControlMode() {
        if (this.currentMode == 3 || this.currentMode == 4) {
            Log.d("ModeController", "User Control Mode cannot start in pair/patrol/userControl");
            return;
        }
        if (this.currentMode == 5) {
            Log.d("ModeController", "User Control Mode already start");
        }
        stopSleepTimer();
        Log.d("ModeController", "starting user control...");
        startUserControl();
    }

    public void stopUserControlMode() {
        if (this.currentMode != 5) {
            Log.d("ModeController", "stop user control mode in other mode...");
            return;
        }
        Log.d("ModeController", "stopping user control...");
        restartSleepTimer();
        stopUserControl();
    }

    private void startUserControl() {
        setMode(5);
        CentralController centralController = CentralController.getInstance(this.context);
        centralController.stopFaceDetection();
    }

    private void stopUserControl() {
        CentralController centralController = CentralController.getInstance(this.context);
        centralController.startFaceDetection();
        setMode(1);
    }

    public void debugConnectWIFI(String ssid, String password) {
        this.pairModeController.connectWifi(ssid, password);
    }
}
