package com.bullb.r2d2_nanopisystem.WebSocket;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CommandReceiver;
import java.util.Timer;
import java.util.TimerTask;
import org.java_websocket.WebSocket;

/* JADX INFO: loaded from: classes.dex */
public class SocketConnection {
    private static final int TIMER_TIMEOUT = 10000;
    private CommandReceiver commandReceiver;
    private ControlTimeoutTask controlTimeoutTask;
    private Timer controlTimer;
    private SocketServer socketServer;
    private WebSocket webSocket;
    private final String TAG = "SocketConnection";
    private String clientUUID = null;
    private boolean controlling = false;
    private int seq = 0;
    private boolean isValidConnection = false;
    private Timer establishConnectionTimer = new Timer();

    public SocketConnection(Context context, @NonNull WebSocket websocket, SocketServer socketServer) {
        this.webSocket = websocket;
        this.establishConnectionTimer.schedule(new StopConnectionTimerTask(), 10000L);
        this.commandReceiver = new CommandReceiver(context, this);
        this.socketServer = socketServer;
        this.controlTimer = new Timer();
    }

    public WebSocket getWebSocket() {
        return this.webSocket;
    }

    public boolean isValidConnection() {
        return this.isValidConnection;
    }

    public void setValidConnection(boolean validConnection) {
        this.isValidConnection = validConnection;
    }

    public void validateConnection(String clientUUID) {
        this.clientUUID = clientUUID;
        setValidConnection(true);
    }

    public boolean isControlling() {
        return this.controlling;
    }

    public void setControlling(boolean controlling) {
        boolean controllingStateBefore = this.controlling;
        this.controlling = controlling;
        if (controllingStateBefore != controlling) {
            this.socketServer.checkControlModeNeeded();
        }
        if (this.controlTimeoutTask != null) {
            this.controlTimeoutTask.cancel();
        }
        if (controlling) {
            this.controlTimeoutTask = new ControlTimeoutTask();
            this.controlTimer.schedule(this.controlTimeoutTask, 12000L);
        }
    }

    public void close() {
        synchronized (this.webSocket) {
            this.webSocket.close();
        }
    }

    public void send(String data) {
        try {
            synchronized (this.webSocket) {
                this.webSocket.send(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getClientUUID() {
        return this.clientUUID;
    }

    public void receiveMessage(String data) {
        this.commandReceiver.interpretCommand(data);
    }

    public void stopEstablishTimer() {
        if (this.establishConnectionTimer != null) {
            this.establishConnectionTimer.cancel();
            this.establishConnectionTimer = null;
        }
    }

    private class StopConnectionTimerTask extends TimerTask {
        private final String TAG;

        private StopConnectionTimerTask() {
            this.TAG = "StopBluetoothTimer";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.d("StopBluetoothTimer", "triggered");
            SocketConnection.this.close();
        }
    }

    private class ControlTimeoutTask extends TimerTask {
        private final String TAG;

        private ControlTimeoutTask() {
            this.TAG = "Control Timer";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            Log.d("Control Timer", "triggered");
            SocketConnection.this.setControlling(false);
        }
    }
}
