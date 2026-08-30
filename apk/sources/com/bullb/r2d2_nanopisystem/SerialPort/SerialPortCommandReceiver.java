package com.bullb.r2d2_nanopisystem.SerialPort;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.LEDLightController;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.Model.Command;
import com.bullb.r2d2_nanopisystem.Model.GinResponse;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.bullb.r2d2_nanopisystem.utils.SharedUtils;
import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class SerialPortCommandReceiver {
    private static final String TAG = "SerialPortCmdReceiver";
    private Context context;
    private EventHandler eventHandler;
    private long debounceChargingLastChanged = 0;
    private Timer debounceChargingTimer = new Timer();
    private Gson gson = new Gson();

    public SerialPortCommandReceiver(Context context) {
        this.context = context;
        this.eventHandler = EventHandler.getInstance(context);
    }

    public void interpretCommand(String incomeData) {
        GinResponse ginResponse;
        try {
            Log.d(TAG, "interpret: " + incomeData);
            Command command = (Command) this.gson.fromJson(incomeData, Command.class);
            if (command != null) {
                if (command.cmd.equals(Commander.PLAY_SOUND)) {
                    boolean interrupt = false;
                    if (command.interrupt == 1) {
                        interrupt = true;
                    }
                    this.eventHandler.playSound(Integer.valueOf(command.sound_id).intValue(), interrupt);
                } else if (command.cmd.equals(Commander.READY)) {
                    this.eventHandler.cancelReady();
                } else if (command.cmd.equals("btn")) {
                    switch (command.value) {
                        case 1:
                            ModeController.getInstance(this.context).wake();
                            this.eventHandler.powerOff();
                            break;
                        case 2:
                            ModeController.getInstance(this.context).wake();
                            WifiService.getInstance(this.context).apModeToggle();
                            break;
                        case 3:
                            if (ModeController.getInstance(this.context).getMode() == 3) {
                                ModeController.getInstance(this.context).stopPairMode();
                            } else {
                                ModeController.getInstance(this.context).startPairMode();
                            }
                            break;
                        case 4:
                            ModeController.getInstance(this.context).wake();
                            this.eventHandler.lightsaber();
                            break;
                        case 5:
                            ModeController.getInstance(this.context).wake();
                            this.eventHandler.arm();
                            break;
                        case 6:
                            this.eventHandler.patrol();
                            break;
                    }
                } else if (command.cmd.equals(Commander.GIN) && (ginResponse = (GinResponse) this.gson.fromJson(incomeData, GinResponse.class)) != null) {
                    updateRobot(ginResponse);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateRobot(GinResponse ginResponse) {
        boolean updated = false;
        boolean arm = false;
        boolean lightsaber = false;
        if (ginResponse.lightsaber == 1) {
            lightsaber = true;
        }
        if (ginResponse.arm == 1) {
            arm = true;
        }
        int oldBattery = RobotPreference.getRobotBattery(this.context);
        int updateBattery = ginResponse.batt;
        final int charging = ginResponse.chargingStatus;
        String error = ginResponse.getError();
        if (oldBattery != updateBattery) {
            RobotPreference.setRobotBattery(this.context, ginResponse.batt);
            updated = true;
            if ((oldBattery < SharedUtils.LOW_BATTERY_PERCENTAGE && updateBattery >= SharedUtils.LOW_BATTERY_PERCENTAGE) || (oldBattery >= SharedUtils.LOW_BATTERY_PERCENTAGE && updateBattery < SharedUtils.LOW_BATTERY_PERCENTAGE)) {
                LEDLightController.getInstance(this.context).restoreAll();
            }
        }
        if (RobotPreference.getLightsaber(this.context) != lightsaber) {
            RobotPreference.setRobotLightsaber(this.context, lightsaber);
            updated = true;
        }
        if (RobotPreference.getRobotCharging(this.context) != charging) {
            final long changingTime = System.currentTimeMillis();
            this.debounceChargingLastChanged = changingTime;
            Log.d("DEBOUNCE_CHARGING", "new charging state: " + charging + ", changeId: " + changingTime);
            if (charging == 1) {
                LEDLightController.getInstance(this.context).chargingLightStart();
                RobotPreference.setRobotCharging(this.context, charging);
                updated = true;
            } else {
                this.debounceChargingTimer.schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.SerialPort.SerialPortCommandReceiver.1
                    @Override // java.util.TimerTask, java.lang.Runnable
                    public void run() {
                        if (SerialPortCommandReceiver.this.debounceChargingLastChanged == changingTime) {
                            if (RobotPreference.getRobotCharging(SerialPortCommandReceiver.this.context) != charging) {
                                RobotPreference.setRobotCharging(SerialPortCommandReceiver.this.context, charging);
                                SharedUtils.notifyRobotChanged(SerialPortCommandReceiver.this.context);
                            }
                            if (charging == 2) {
                                LEDLightController.getInstance(SerialPortCommandReceiver.this.context).chargedLightStart();
                            } else if (charging == 0) {
                                LEDLightController.getInstance(SerialPortCommandReceiver.this.context).chargingLightStop();
                            }
                            Log.d("DEBOUNCE_CHARGING", "changed to " + charging + ", changeId: " + changingTime);
                            return;
                        }
                        Log.d("DEBOUNCE_CHARGING", "skipped, changeId: " + changingTime);
                    }
                }, 3000L);
            }
        }
        if (RobotPreference.getRobotArm(this.context) != arm) {
            RobotPreference.setRobotArm(this.context, arm);
            updated = true;
        }
        if (RobotPreference.getRobotProjector(this.context) != ginResponse.projector) {
            RobotPreference.setRobotProjector(this.context, ginResponse.projector);
            updated = true;
        }
        if (RobotPreference.getRobotLongLCD(this.context) != ginResponse.getLCD_l()) {
            RobotPreference.setRobotLongLCD(this.context, ginResponse.getLCD_l());
            updated = true;
        }
        if (RobotPreference.getRobotShortLCD(this.context) != ginResponse.getLcd_s()) {
            RobotPreference.setRobotShortLCD(this.context, ginResponse.getLcd_s());
            updated = true;
        }
        if (!RobotPreference.getRobotError(this.context).equals(error)) {
            RobotPreference.setRobotError(this.context, error);
            updated = true;
        }
        Log.i(TAG, "log Gin command");
        if (updated) {
            SharedUtils.notifyRobotChanged(this.context);
        }
    }

    public void appendLog(String text) {
        File externalStorageDir = Environment.getExternalStorageDirectory();
        File logFile = new File(externalStorageDir, "gin.txt");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append((CharSequence) text);
            buf.newLine();
            buf.close();
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }

    public boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException e) {
            try {
                new JSONArray(test);
            } catch (JSONException e2) {
                return false;
            }
        }
        return true;
    }
}
