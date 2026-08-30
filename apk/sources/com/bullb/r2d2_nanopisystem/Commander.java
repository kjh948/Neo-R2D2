package com.bullb.r2d2_nanopisystem;

import android.content.Context;
import com.bullb.r2d2_nanopisystem.Model.EventJob.ModeJob;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.bullb.r2d2_nanopisystem.SerialPort.SerialPort;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class Commander {
    public static final String ARM = "arm";
    public static final String DEBUG = "debug";
    public static final String GIN = "gin";
    public static final String LCD = "lcd";
    public static final String LED = "led";
    public static final String LIGHTSABER = "lightsaber";
    public static final String MODE = "mode";
    public static final String MOVE = "move";
    public static final String MOVE_HEAD_ANGLE = "head-angle";
    public static final String MOVE_HEAD_DIR = "head-dir";
    public static final String MOVE_HEAD_SHIFT = "head-shift";
    public static final String PLAY_SOUND = "play_sound";
    public static final String POWER_OFF = "shut-down";
    public static final String PROJECTOR = "projector";
    public static final String READY = "ready";
    public static final String RESET = "reset-wdt";
    public static final String SET_HEAD_DIR_POWER = "d-head-power";
    public static final String SET_LEG_POWER = "d-leg-power";
    public static final String STATUS = "status";
    private static Commander commander;
    Context context;
    private SerialPort serialPort;

    public static synchronized Commander getInstance(Context context) {
        if (commander == null) {
            commander = new Commander(context);
        }
        return commander;
    }

    public Commander(Context context) {
        this.context = context;
        this.serialPort = SerialPort.getInstance(context);
    }

    public void softwareReady() {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", READY);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public void move(int power, int angle) {
        if (RobotPreference.getRobotCharging(this.context) == 0) {
            JSONObject json = new JSONObject();
            try {
                json.put("cmd", MOVE);
                json.put(RobotApiHandler.POWER, power);
                json.put("angle", angle);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.serialPort.send(json.toString());
        }
    }

    public void moveHeadAngle(int angle) {
        if (RobotPreference.getRobotCharging(this.context) == 0) {
            JSONObject json = new JSONObject();
            try {
                json.put("cmd", MOVE_HEAD_ANGLE);
                json.put("angle", angle);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.serialPort.send(json.toString());
        }
    }

    public void headShift(int angle) {
        if (RobotPreference.getRobotCharging(this.context) == 0) {
            JSONObject json = new JSONObject();
            try {
                json.put("cmd", MOVE_HEAD_SHIFT);
                json.put("angle", angle);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.serialPort.send(json.toString());
        }
    }

    public void moveHeadDirection(int direction) {
        if (RobotPreference.getRobotCharging(this.context) == 0) {
            JSONObject json = new JSONObject();
            try {
                json.put("cmd", MOVE_HEAD_DIR);
                json.put("dir", direction);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.serialPort.send(json.toString());
        }
    }

    public boolean mode(int mode) {
        if (RobotPreference.getRobotCharging(this.context) != 0) {
            for (int i : ModeJob.prohabittedModeWhileCharging) {
                if (mode == i) {
                    return false;
                }
            }
        }
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", MODE);
            json.put(MODE, mode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this.serialPort.send(json.toString());
    }

    public boolean projectorMode(int mode) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", PROJECTOR);
            json.put(MODE, mode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this.serialPort.send(json.toString());
    }

    public boolean extendArm(int power) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", ARM);
            json.put(RobotApiHandler.POWER, power);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this.serialPort.send(json.toString());
    }

    public boolean lightsaber(int power) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", LIGHTSABER);
            json.put(RobotApiHandler.POWER, power);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this.serialPort.send(json.toString());
    }

    public void LED(int r, int b, int y, int g) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", LED);
            if (r != -1) {
                json.put("r", r);
            }
            if (b != -1) {
                json.put("b", b);
            }
            if (y != -1) {
                json.put("y", y);
            }
            if (g != -1) {
                json.put("g", g);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public boolean LCD(int s, int l) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", LCD);
            if (s != -1) {
                json.put("s", s);
            }
            if (l != -1) {
                json.put("l", l);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this.serialPort.send(json.toString());
    }

    public void debug() {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", DEBUG);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public void changeHeadPower(int power) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", SET_HEAD_DIR_POWER);
            json.put(RobotApiHandler.POWER, power);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public void changeRobotLegPower(int power) {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", SET_LEG_POWER);
            json.put(RobotApiHandler.POWER, power);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public void reset() {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", RESET);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public void gin() {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", GIN);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.serialPort.send(json.toString());
    }

    public boolean powerOff() {
        JSONObject json = new JSONObject();
        try {
            json.put("cmd", POWER_OFF);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this.serialPort.send(json.toString());
    }
}
