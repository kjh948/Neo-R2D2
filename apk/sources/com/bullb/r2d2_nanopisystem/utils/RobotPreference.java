package com.bullb.r2d2_nanopisystem.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import com.bullb.r2d2_nanopisystem.Commander;
import com.bullb.r2d2_nanopisystem.Model.EventJob.Client;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class RobotPreference {
    public static final int CHARGING_STATUS_CHARGING = 1;
    public static final int CHARGING_STATUS_CHARGING_FINISHED = 2;
    public static final int CHARGING_STATUS_NOT_CHARGED = 0;
    public static final String NO_ERROR = "NO ERROR";
    public static final int UPDATE_STATUS_DOWNLADING = 1;
    public static final int UPDATE_STATUS_INSTALLING = 2;
    public static final int UPDATE_STATUS_NOT_UPDATING = 0;
    private static String ROBOT_PREFERENCE_NAME = "robot";
    private static String ROBOT_NAME = "robot_name";
    private static String ROBOT_UDID = "robot_udid";
    private static String ROBOT_ACCESS_KEY = "robot_access_key";
    private static String CLIENT_LIST = "clientList";
    private static String ROBOT_SETTING_ENABLE_FACE_DETECTION = RobotApiHandler.FACE_DETECTION;
    private static String ROBOT_SETTING_ENABLE_VOICE_RECOGNITION = "voiceRecognition";
    private static String ROBOT_SETTING_ENABLE_MUTE = RobotApiHandler.MUTE;
    private static String ROBOT_BATTERY = "battery";
    private static String ROBOT_SELF_UPDATE_STATE = "self_update_state";
    private static String ROBOT_UPDATE_DL_PROGRESS = "update_dl_progress";
    private static String ROBOT_LIGHTSABER = Commander.LIGHTSABER;
    private static String ROBOT_ARM = Commander.ARM;
    private static String ROBOT_PROJECTOR = Commander.PROJECTOR;
    private static String ROBOT_RESTART_VERSION = "restart_version";
    private static String ROBOT_SHORT_LCD = "lcd_s";
    private static String ROBOT_LONG_LCD = "lcd_l";
    private static String ROBOT_CHARGING = "robot_charging";
    private static String ROBOT_ERROR = "robot_error";

    public static void setRobotName(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(ROBOT_NAME, name);
        e.commit();
    }

    public static String getRobotName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        String name = prefs.getString(ROBOT_NAME, null);
        return name;
    }

    public static void setRobotUdid(Context context, String udid) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(ROBOT_UDID, udid);
        e.commit();
    }

    public static String getRobotUdid(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        String udid = prefs.getString(ROBOT_UDID, null);
        return udid;
    }

    public static void setClientList(Context context, ArrayList<Client> clientList) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(CLIENT_LIST, new Gson().toJson(clientList));
        e.apply();
    }

    @NonNull
    public static ArrayList<Client> getClientList(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        String resultString = prefs.getString(CLIENT_LIST, null);
        ArrayList<Client> robotList = (ArrayList) new Gson().fromJson(resultString, new TypeToken<ArrayList<Client>>() { // from class: com.bullb.r2d2_nanopisystem.utils.RobotPreference.1
        }.getType());
        if (robotList == null) {
            return new ArrayList<>();
        }
        return robotList;
    }

    public static void setRobotAccessKey(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(ROBOT_ACCESS_KEY, key);
        e.commit();
    }

    public static String getRobotAccessKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        String key = prefs.getString(ROBOT_ACCESS_KEY, null);
        return key;
    }

    public static boolean isEnabledMute(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean isEnabled = prefs.getBoolean(ROBOT_SETTING_ENABLE_MUTE, false);
        return isEnabled;
    }

    public static void setEnabledMute(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_SETTING_ENABLE_MUTE, enabled);
        e.commit();
    }

    public static boolean isEnabledFaceDetection(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean isEnabled = prefs.getBoolean(ROBOT_SETTING_ENABLE_FACE_DETECTION, true);
        return isEnabled;
    }

    public static void setEnabledFaceDetection(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_SETTING_ENABLE_FACE_DETECTION, enabled);
        e.commit();
    }

    public static boolean isEnabledVoiceRecognition(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean isEnabled = prefs.getBoolean(ROBOT_SETTING_ENABLE_VOICE_RECOGNITION, true);
        return isEnabled;
    }

    public static void setEnabledVoiceRecognition(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_SETTING_ENABLE_VOICE_RECOGNITION, enabled);
        e.commit();
    }

    public static int getRobotBattery(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        int battery = prefs.getInt(ROBOT_BATTERY, -1);
        return battery;
    }

    public static void setRobotBattery(Context context, int battery) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(ROBOT_BATTERY, battery);
        e.commit();
    }

    public static int getRobotSelfUpdate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        int battery = prefs.getInt(ROBOT_SELF_UPDATE_STATE, 0);
        return battery;
    }

    public static void setRobotSelfUpdate(Context context, int selfUpdateState) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(ROBOT_SELF_UPDATE_STATE, selfUpdateState);
        e.commit();
    }

    public static int getRobotUpdateDownloadProgress(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        int battery = prefs.getInt(ROBOT_UPDATE_DL_PROGRESS, 0);
        return battery;
    }

    public static void setRobotUpdateDownloadProgress(Context context, int downloadProgress) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(ROBOT_UPDATE_DL_PROGRESS, downloadProgress);
        e.commit();
    }

    public static boolean getLightsaber(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean lightsaber = prefs.getBoolean(ROBOT_LIGHTSABER, false);
        return lightsaber;
    }

    public static void setRobotLightsaber(Context context, boolean lightsaber) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_LIGHTSABER, lightsaber);
        e.commit();
    }

    public static boolean getRobotArm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean arm = prefs.getBoolean(ROBOT_ARM, false);
        return arm;
    }

    public static void setRobotArm(Context context, boolean arm) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_ARM, arm);
        e.commit();
    }

    public static int getRobotProjector(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        int projector = prefs.getInt(ROBOT_PROJECTOR, 0);
        return projector;
    }

    public static void setRobotProjector(Context context, int projector) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(ROBOT_PROJECTOR, projector);
        e.commit();
    }

    public static int getRobotRestartVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        int version = prefs.getInt(ROBOT_RESTART_VERSION, 0);
        return version;
    }

    public static void addRobotRestartVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        int version = prefs.getInt(ROBOT_RESTART_VERSION, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(ROBOT_RESTART_VERSION, version + 1);
        e.commit();
    }

    public static boolean getRobotShortLCD(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean enabled = prefs.getBoolean(ROBOT_SHORT_LCD, false);
        return enabled;
    }

    public static void setRobotShortLCD(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_SHORT_LCD, enabled);
        e.commit();
    }

    public static boolean getRobotLongLCD(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        boolean enabled = prefs.getBoolean(ROBOT_LONG_LCD, false);
        return enabled;
    }

    public static void setRobotLongLCD(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(ROBOT_LONG_LCD, enabled);
        e.commit();
    }

    public static void setRobotCharging(Context context, int chargingStatus) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(ROBOT_CHARGING, chargingStatus);
        e.commit();
    }

    public static int getRobotCharging(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        return prefs.getInt(ROBOT_CHARGING, 0);
    }

    public static void setRobotError(Context context, String error) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(ROBOT_ERROR, error);
        e.commit();
    }

    public static String getRobotError(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(ROBOT_PREFERENCE_NAME, 0);
        return prefs.getString(ROBOT_ERROR, NO_ERROR);
    }

    public static void clearPreference(Context context) {
        setRobotAccessKey(context, null);
        setClientList(context, null);
        setEnabledVoiceRecognition(context, true);
        setEnabledFaceDetection(context, true);
        setRobotBattery(context, -1);
        setRobotSelfUpdate(context, 0);
        setRobotUpdateDownloadProgress(context, 0);
        setRobotLightsaber(context, false);
        setRobotArm(context, false);
        setRobotProjector(context, -1);
        setRobotLongLCD(context, false);
        setRobotShortLCD(context, false);
        setRobotCharging(context, 0);
        setRobotError(context, NO_ERROR);
    }
}
