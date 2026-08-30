package com.bullb.r2d2_nanopisystem.RobotApi;

import android.content.Context;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.Bluetooth.BluetoothService;
import com.bullb.r2d2_nanopisystem.CentralController;
import com.bullb.r2d2_nanopisystem.EventHandler;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.Model.EventJob.Client;
import com.bullb.r2d2_nanopisystem.Model.Robot;
import com.bullb.r2d2_nanopisystem.Model.Wifi;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.BaseRequest;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.ChangeNameRequest;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.ConnectWifiRequest;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.GrantAccessRequest;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.ToggleRequest;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.UnpairRequest;
import com.bullb.r2d2_nanopisystem.RobotApi.Request.UserControlRequest;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.bullb.r2d2_nanopisystem.WebSocket.SocketConnection;
import com.bullb.r2d2_nanopisystem.WebSocket.SocketServer;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class RobotApiHandler {
    private final int SOURCE_BLUETOOTH;
    private final int SOURCE_WEBSOCKET;
    private final String TAG;
    private Timer WifiConnectTimer;
    private BluetoothService.ConnectedThread connectedThread;
    private Context context;
    private Gson gson;
    private SocketConnection socketConnection;
    private int source;
    private WifiService.WifiConnectionStateChangedListener wifiConnectionStateChangedListener;
    private WifiService wifiService;
    public static final String GrantPermission = "grantAccess";
    public static final ArrayList ROBOT_AUTH_COMMAND_LIST = new ArrayList(Arrays.asList(GrantPermission));
    public static final String GET_WIFI_LIST = "getWifiList";
    public static final String CONNECT_WIFI = "connectWifi";
    public static final String FACE_DETECTION = "face_detection";
    public static final String VOICE_RECOGNITION = "voice_recognition";
    public static final String MUTE = "mute";
    public static final String POWER = "power";
    public static final String USER_CONTROL = "user_control";
    public static final String CHANGE_NAME = "change_name";
    public static final String GET_PAIRED_LIST = "paired_list";
    public static final String UNPAIR = "unpair";
    public static final ArrayList ROBOT_NORMAL_COMMAND_LIST = new ArrayList(Arrays.asList(GET_WIFI_LIST, CONNECT_WIFI, FACE_DETECTION, VOICE_RECOGNITION, MUTE, POWER, USER_CONTROL, CHANGE_NAME, GET_PAIRED_LIST, UNPAIR));

    public RobotApiHandler(Context context) {
        this.SOURCE_BLUETOOTH = 1;
        this.SOURCE_WEBSOCKET = 2;
        this.TAG = "RobotApiHandler";
        this.source = -1;
        this.context = context;
        this.gson = new Gson();
        this.wifiService = WifiService.getInstance(context);
    }

    public RobotApiHandler(Context context, BluetoothService.ConnectedThread connectedThread) {
        this(context);
        this.connectedThread = connectedThread;
        this.source = 1;
    }

    public RobotApiHandler(Context context, SocketConnection socketConnection) {
        this(context);
        this.socketConnection = socketConnection;
        this.source = 2;
    }

    public void handleAuthCommand(String cmd, String line) {
        switch (cmd) {
            case "grantAccess":
                grantAccessToClient(line);
                break;
        }
    }

    public void handleNormalCommand(String cmd, String line) {
        switch (cmd) {
            case "getWifiList":
                getWifiList(line);
                break;
            case "connectWifi":
                connectWifi(line);
                break;
            case "face_detection":
                faceDetectionToggle(line);
                break;
            case "mute":
                muteToggle(line);
                break;
            case "power":
                powerOff(line);
                break;
            case "voice_recognition":
                voiceRecognitionToggle(line);
                break;
            case "user_control":
                userControl(line);
                break;
            case "change_name":
                changeName(line);
                break;
            case "paired_list":
                getPairedList(line);
                break;
            case "unpair":
                unPair(line);
                break;
        }
    }

    public void unPair(String json) {
        Log.d("RobotApiHandler", UNPAIR);
        UnpairRequest request = (UnpairRequest) this.gson.fromJson(json, UnpairRequest.class);
        JSONObject jsonObject = new JSONObject();
        try {
            if (request.getUUID() == null) {
                RobotPreference.setClientList(this.context, null);
                Gson gson = new Gson();
                String listString = gson.toJson(RobotPreference.getClientList(this.context), new TypeToken<ArrayList<Client>>() { // from class: com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler.1
                }.getType());
                jsonObject.put("clients", new JSONArray(listString));
                responseSuccess(jsonObject, request.cmd, request.seq);
                SocketServer.getInstance(this.context).clearAll();
                Log.d(UNPAIR, "unpaired all");
            } else {
                ArrayList<Client> storedList = RobotPreference.getClientList(this.context);
                int clientIndex = storedList.indexOf(new Client(request.getUUID(), null));
                if (clientIndex == -1) {
                    responseFail(jsonObject, 423, request.cmd, request.seq);
                    Log.d(UNPAIR, "uuid not found");
                } else {
                    storedList.remove(clientIndex);
                    RobotPreference.setClientList(this.context, storedList);
                    Gson gson2 = new Gson();
                    String listString2 = gson2.toJson(storedList, new TypeToken<ArrayList<Client>>() { // from class: com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler.2
                    }.getType());
                    jsonObject.put("clients", new JSONArray(listString2));
                    responseSuccess(jsonObject, request.cmd, request.seq);
                    SocketServer.getInstance(this.context).closeClient(request.getUUID());
                    Log.d(UNPAIR, "client removed");
                }
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
            responseFail(jsonObject, 1, request.cmd, request.seq);
        } catch (JSONException e2) {
            e2.printStackTrace();
            responseFail(jsonObject, 1, request.cmd, request.seq);
        }
    }

    public void getPairedList(String json) {
        Log.d("RobotApiHandler", "getPairedList");
        BaseRequest request = (BaseRequest) this.gson.fromJson(json, BaseRequest.class);
        JSONObject jsonObject = new JSONObject();
        try {
            String listString = this.gson.toJson(RobotPreference.getClientList(this.context), new TypeToken<ArrayList<Client>>() { // from class: com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler.3
            }.getType());
            JSONArray jsonArray = new JSONArray(listString);
            jsonObject.put("clients", jsonArray);
            responseSuccess(jsonObject, request.cmd, request.seq);
        } catch (JSONException e) {
            e.printStackTrace();
            responseFail(jsonObject, 1, request.cmd, request.seq);
        }
    }

    public void changeName(String json) {
        Log.d("RobotApiHandler", "changeName");
        ChangeNameRequest request = (ChangeNameRequest) this.gson.fromJson(json, ChangeNameRequest.class);
        JSONObject jsonObject = new JSONObject();
        if (request.getNewName() == null || request.getNewName().isEmpty() || request.getNewName().length() > 16) {
            responseFail(jsonObject, 422, request.cmd, request.seq);
        } else {
            RobotPreference.setRobotName(this.context, request.getNewName());
            responseSuccess(jsonObject, request.cmd, request.seq);
        }
    }

    public void userControl(String json) {
        Log.d("RobotApiHandler", "userControl");
        UserControlRequest request = (UserControlRequest) this.gson.fromJson(json, UserControlRequest.class);
        new JSONObject();
        if (request.isEnable()) {
            this.socketConnection.setControlling(true);
        } else {
            this.socketConnection.setControlling(false);
        }
    }

    private void powerOff(String json) {
        ToggleRequest request = (ToggleRequest) this.gson.fromJson(json, ToggleRequest.class);
        ModeController.getInstance(this.context).wake();
        EventHandler.getInstance(this.context).powerOff();
        JSONObject jsonObject = new JSONObject();
        responseSuccess(jsonObject, request.cmd, request.seq);
    }

    private void muteToggle(String json) {
        Log.d("RobotApiHandler", "muteToggle");
        ToggleRequest request = (ToggleRequest) this.gson.fromJson(json, ToggleRequest.class);
        JSONObject jsonObject = new JSONObject();
        CentralController centralController = CentralController.getInstance(this.context);
        boolean isMuteEnable = RobotPreference.isEnabledMute(this.context);
        RobotPreference.setEnabledMute(this.context, request.isEnable());
        if (isMuteEnable != request.isEnable()) {
            centralController.setMute(request.isEnable());
            Log.d("RobotApiHandler", "faceDetectionToggle:" + String.valueOf(request.isEnable()));
        }
        responseSuccess(jsonObject, request.cmd, request.seq);
    }

    private void faceDetectionToggle(String json) {
        Log.d("RobotApiHandler", "faceDetectionToggle");
        ToggleRequest request = (ToggleRequest) this.gson.fromJson(json, ToggleRequest.class);
        JSONObject jsonObject = new JSONObject();
        CentralController centralController = CentralController.getInstance(this.context);
        boolean isFaceEnable = RobotPreference.isEnabledFaceDetection(this.context);
        RobotPreference.setEnabledFaceDetection(this.context, request.isEnable());
        if (isFaceEnable != request.isEnable()) {
            if (request.isEnable()) {
                centralController.startFaceDetection();
            } else {
                centralController.stopFaceDetection();
            }
            Log.d("RobotApiHandler", "faceDetectionToggle:" + String.valueOf(request.isEnable()));
        }
        responseSuccess(jsonObject, request.cmd, request.seq);
    }

    private void voiceRecognitionToggle(String json) {
        Log.d("RobotApiHandler", "voiceRecognitionToggle");
        ToggleRequest request = (ToggleRequest) this.gson.fromJson(json, ToggleRequest.class);
        JSONObject jsonObject = new JSONObject();
        CentralController centralController = CentralController.getInstance(this.context);
        boolean isVoiceEnable = RobotPreference.isEnabledVoiceRecognition(this.context);
        RobotPreference.setEnabledVoiceRecognition(this.context, request.isEnable());
        if (isVoiceEnable != request.isEnable()) {
            if (request.isEnable()) {
                centralController.startVoiceRecognition();
            } else {
                centralController.stopVoiceRecognition();
            }
            Log.d("RobotApiHandler", "voiceRecognitionToggle:" + String.valueOf(request.isEnable()));
        }
        responseSuccess(jsonObject, request.cmd, request.seq);
    }

    private void getWifiList(String json) {
        Log.d("RobotApiHandler", "get wifi list");
        BaseRequest request = (BaseRequest) this.gson.fromJson(json, BaseRequest.class);
        JSONObject jsonObject = new JSONObject();
        try {
            String listString = this.gson.toJson(this.wifiService.getScanResult(), new TypeToken<ArrayList<Wifi>>() { // from class: com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler.4
            }.getType());
            JSONArray jsonArray = new JSONArray(listString);
            jsonObject.put("wifi_list", jsonArray);
            jsonObject.put("currentSSID", this.wifiService.getCurrentNetworkSSID());
            responseSuccess(jsonObject, request.cmd, request.seq);
        } catch (JSONException e) {
            e.printStackTrace();
            responseFail(jsonObject, 1, request.cmd, request.seq);
        }
    }

    public void connectWifi(String json) {
        Log.d("RobotApiHandler", "connect Wifi command");
        final ConnectWifiRequest request = (ConnectWifiRequest) this.gson.fromJson(json, ConnectWifiRequest.class);
        final JSONObject jsonObject = new JSONObject();
        final WifiService wifiService = WifiService.getInstance(this.context);
        int result = wifiService.connectWifi(request.getSSID(), request.getPassword());
        if (result > 0) {
            responseFail(jsonObject, result, request.cmd, request.seq);
            return;
        }
        if (result == 0) {
            responseSuccess(jsonObject, request.cmd, request.seq);
            return;
        }
        this.WifiConnectTimer = new Timer();
        this.WifiConnectTimer.schedule(new StopWifiConnectTask(), 30000L);
        this.wifiConnectionStateChangedListener = new WifiService.WifiConnectionStateChangedListener() { // from class: com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler.5
            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onConnected() {
                RobotApiHandler.this.responseSuccess(jsonObject, request.cmd, request.seq);
                wifiService.removeWifiConnectionStateChangeListener(this);
                RobotApiHandler.this.WifiConnectTimer.cancel();
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onConnecting() {
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onDisconnected() {
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onAuthenticating() {
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onUnauthorized() {
                RobotApiHandler robotApiHandler = RobotApiHandler.this;
                JSONObject jSONObject = jsonObject;
                wifiService.getClass();
                robotApiHandler.responseFail(jSONObject, 411, request.cmd, request.seq);
                wifiService.removeWifiConnectionStateChangeListener(this);
                RobotApiHandler.this.WifiConnectTimer.cancel();
            }

            @Override // com.bullb.r2d2_nanopisystem.WIFI.WifiService.WifiConnectionStateChangedListener
            public void onFailed() {
            }
        };
        wifiService.addWifiConnectionStateChangeListener(this.wifiConnectionStateChangedListener);
    }

    private class StopWifiConnectTask extends TimerTask {
        private final String TAG;

        private StopWifiConnectTask() {
            this.TAG = "WifiConnectTimer";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.i("WifiConnectTimer", "Wifi Timer trigger");
            RobotApiHandler.this.wifiService.removeWifiConnectionStateChangeListener(RobotApiHandler.this.wifiConnectionStateChangedListener);
        }
    }

    private void grantAccessToClient(String json) {
        GrantAccessRequest request = (GrantAccessRequest) this.gson.fromJson(json, GrantAccessRequest.class);
        Log.d("RobotApiHandler", "try grantAccessToClient");
        JSONObject jsonObject = new JSONObject();
        if (request.getUUID() == null || request.getUUID().isEmpty()) {
            responseFail(jsonObject, 301, request.cmd, request.seq);
            closeConnection();
            return;
        }
        ArrayList<Client> clientList = RobotPreference.getClientList(this.context);
        Client accessClient = new Client(request.getUUID(), request.getDeviceName());
        boolean oldClient = false;
        if (clientList.contains(accessClient)) {
            oldClient = true;
        }
        ModeController modeController = ModeController.getInstance(this.context);
        if (WifiService.getInstance(this.context).isAPMode() || oldClient || modeController.getMode() == 3) {
            if (!oldClient) {
                Log.d("RobotApiHandler", "new Client");
                clientList.add(accessClient);
                RobotPreference.setClientList(this.context, clientList);
            }
            try {
                Robot robot = new Robot(this.context, true);
                jsonObject.put("robot", new JSONObject(new Gson().toJson(robot)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            modeController.successConnectionInPairMode();
            validateConnection(request.getUUID());
            responseSuccess(jsonObject, request.cmd, request.seq);
            return;
        }
        responseFail(jsonObject, 401, request.cmd, request.seq);
        closeConnection();
    }

    public void responseSuccess(JSONObject jsonObject, String cmd, int seq) {
        try {
            jsonObject.put("resultCode", 0);
            jsonObject.put("cmd", cmd);
            jsonObject.put("seq", seq);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d("RobotApiHandler", jsonObject.toString());
        send(jsonObject.toString() + "\n");
    }

    public void responseFail(JSONObject jsonObject, int errorCode, String cmd, int seq) {
        try {
            jsonObject.put("resultCode", errorCode);
            jsonObject.put("cmd", cmd);
            jsonObject.put("seq", seq);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d("RobotApiHandler", jsonObject.toString());
        send(jsonObject.toString() + "\n");
    }

    private void validateConnection(String clientUUID) {
        if (this.source == 1) {
            this.connectedThread.stopEstablishTimer();
            this.connectedThread.validateConnection(clientUUID);
        } else if (this.source == 2) {
            this.socketConnection.stopEstablishTimer();
            this.socketConnection.validateConnection(clientUUID);
        }
    }

    private void closeConnection() {
        if (this.source == 1) {
            this.connectedThread.cancel();
        } else if (this.source == 2) {
            this.socketConnection.close();
        }
    }

    private void send(String data) {
        if (this.source == 1) {
            this.connectedThread.write(data.getBytes());
        } else if (this.source == 2) {
            this.socketConnection.send(data);
        }
    }
}
