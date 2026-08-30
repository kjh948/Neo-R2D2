package com.bullb.r2d2_nanopisystem;

import android.content.Context;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.Bluetooth.BluetoothService;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.Model.Command;
import com.bullb.r2d2_nanopisystem.RobotApi.RobotApiHandler;
import com.bullb.r2d2_nanopisystem.SelfUpdate.AppUpdater;
import com.bullb.r2d2_nanopisystem.WebSocket.SocketConnection;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import org.java_websocket.drafts.Draft_75;

/* JADX INFO: loaded from: classes.dex */
public class CommandReceiver {
    private final int SOURCE_BLUETOOTH;
    private final int SOURCE_WEBSOCKET;
    private final String TAG;
    private BluetoothService.ConnectedThread connectedThread;
    private Context context;
    private String data;
    private EventHandler eventHandler;
    private Gson gson;
    private ArrayList<String> lines;
    private RobotApiHandler robotApiHandler;
    private SocketConnection socketConnection;
    private int source;

    public CommandReceiver(Context context, SocketConnection socketConnection) {
        this(context);
        this.socketConnection = socketConnection;
        this.source = 2;
        this.robotApiHandler = new RobotApiHandler(context, socketConnection);
    }

    public CommandReceiver(Context context, BluetoothService.ConnectedThread connectedThread) {
        this(context);
        this.connectedThread = connectedThread;
        this.source = 1;
        this.robotApiHandler = new RobotApiHandler(context, connectedThread);
    }

    public CommandReceiver(Context context) {
        this.SOURCE_BLUETOOTH = 1;
        this.SOURCE_WEBSOCKET = 2;
        this.TAG = "CommandReceiver";
        this.data = "";
        this.lines = new ArrayList<>();
        this.source = -1;
        this.gson = new Gson();
        this.eventHandler = EventHandler.getInstance(context);
        this.context = context;
    }

    private boolean isValidConnection() {
        if (this.source == 1) {
            return this.connectedThread.isValidConnection();
        }
        if (this.source == 2) {
            return this.socketConnection.isValidConnection();
        }
        return false;
    }

    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    public void interpretCommand(String incomeData) {
        String incomeData2;
        if (incomeData.isEmpty()) {
            for (String line : this.lines) {
                Log.i("CommandReceiver", "Interpret Command: " + line);
                try {
                    Log.d("input json", line);
                    Command command = (Command) this.gson.fromJson(line, Command.class);
                    if (command != null) {
                        String cmd = command.cmd;
                        if (RobotApiHandler.ROBOT_AUTH_COMMAND_LIST.contains(command.cmd)) {
                            this.robotApiHandler.handleAuthCommand(cmd, line);
                        } else if (isValidConnection() && RobotApiHandler.ROBOT_NORMAL_COMMAND_LIST.contains(command.cmd)) {
                            this.robotApiHandler.handleNormalCommand(cmd, line);
                        } else if (isValidConnection() && ModeController.getInstance(this.context).getMode() != 3) {
                            byte b = -1;
                            switch (cmd.hashCode()) {
                                case -1681340887:
                                    if (cmd.equals("self_update_unsafe")) {
                                        b = 12;
                                    }
                                    break;
                                case -1547904740:
                                    if (cmd.equals("self_update")) {
                                        b = 11;
                                    }
                                    break;
                                case -1116940512:
                                    if (cmd.equals(Commander.MOVE_HEAD_DIR)) {
                                        b = 2;
                                    }
                                    break;
                                case -894830916:
                                    if (cmd.equals(Commander.PROJECTOR)) {
                                        b = 3;
                                    }
                                    break;
                                case 106957:
                                    if (cmd.equals(Commander.LCD)) {
                                        b = 7;
                                    }
                                    break;
                                case 107019:
                                    if (cmd.equals(Commander.LED)) {
                                        b = 8;
                                    }
                                    break;
                                case 3357091:
                                    if (cmd.equals(Commander.MODE)) {
                                        b = 9;
                                    }
                                    break;
                                case 3357649:
                                    if (cmd.equals(Commander.MOVE)) {
                                        b = 0;
                                    }
                                    break;
                                case 1021931100:
                                    if (cmd.equals("move-head")) {
                                        b = 1;
                                    }
                                    break;
                                case 1052684893:
                                    if (cmd.equals(Commander.SET_LEG_POWER)) {
                                        b = 6;
                                    }
                                    break;
                                case 1138198497:
                                    if (cmd.equals(Commander.SET_HEAD_DIR_POWER)) {
                                        b = 5;
                                    }
                                    break;
                                case 1925808196:
                                    if (cmd.equals(Commander.PLAY_SOUND)) {
                                        b = 10;
                                    }
                                    break;
                                case 2022645193:
                                    if (cmd.equals(Commander.RESET)) {
                                        b = 4;
                                    }
                                    break;
                                case 2024125103:
                                    if (cmd.equals("reset_mcu")) {
                                        b = Draft_75.CR;
                                    }
                                    break;
                            }
                            switch (b) {
                                case 0:
                                    this.eventHandler.move(command.power, command.angle);
                                    if (command.power > 0 && command.angle == 0) {
                                        new Timer().schedule(new TimerTask() { // from class: com.bullb.r2d2_nanopisystem.CommandReceiver.1
                                            @Override // java.util.TimerTask, java.lang.Runnable
                                            public void run() {
                                                CommandReceiver.this.eventHandler.moveHead(0);
                                            }
                                        }, 100L);
                                    }
                                    break;
                                case 1:
                                    this.eventHandler.moveHead(command.angle);
                                    break;
                                case 2:
                                    this.eventHandler.moveHeadDir(command.dir);
                                    break;
                                case 3:
                                    this.eventHandler.projectorMode(Integer.valueOf(command.mode).intValue());
                                    break;
                                case 4:
                                    this.eventHandler.reset();
                                    break;
                                case 5:
                                    this.eventHandler.changeHeadDirPower(Integer.valueOf(command.power).intValue());
                                    break;
                                case 6:
                                    this.eventHandler.changeLegPower(Integer.valueOf(command.power).intValue());
                                    break;
                                case 7:
                                    int l = -1;
                                    int s = -1;
                                    if (command.s != -1) {
                                        s = command.s;
                                    }
                                    if (command.l != -1) {
                                        l = command.l;
                                    }
                                    this.eventHandler.LCD(s, l);
                                    break;
                                case 8:
                                    int g = -1;
                                    int y = -1;
                                    int b2 = -1;
                                    int r = -1;
                                    if (command.r != -1) {
                                        r = command.r;
                                    }
                                    if (command.b != -1) {
                                        b2 = command.b;
                                    }
                                    if (command.y != -1) {
                                        y = command.y;
                                    }
                                    if (command.g != -1) {
                                        g = command.g;
                                    }
                                    this.eventHandler.LED(r, b2, y, g);
                                    break;
                                case 9:
                                    Log.d("CommandReceiver", Commander.MODE);
                                    this.eventHandler.mode(Integer.valueOf(command.mode).intValue());
                                    break;
                                case 10:
                                    boolean interrupt = false;
                                    if (command.interrupt == 1) {
                                        interrupt = true;
                                    }
                                    this.eventHandler.playSound(Integer.valueOf(command.sound_id).intValue(), interrupt);
                                    break;
                                case 11:
                                    if (command.url != null && RobotPreference.getRobotBattery(this.context) > 50) {
                                        Log.d("CommandReceiver", "self update now: " + command.url);
                                        AppUpdater.getInstance(this.context).updateAPK(command.url);
                                    }
                                    break;
                                case 12:
                                    Log.d("CommandReceiver", "self update now: " + command.url);
                                    if (command.url != null) {
                                        AppUpdater.getInstance(this.context).updateAPK(command.url);
                                    }
                                    break;
                                case 13:
                                    this.eventHandler.resetMCU();
                                    break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            this.lines.clear();
            return;
        }
        if (incomeData.contains("\n")) {
            this.data += incomeData.substring(0, incomeData.indexOf("\n"));
            incomeData2 = incomeData.substring(incomeData.indexOf("\n") + 1);
            this.lines.add(this.data);
            this.data = "";
        } else {
            this.data += incomeData;
            incomeData2 = "";
        }
        interpretCommand(incomeData2);
    }
}
