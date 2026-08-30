package com.bullb.r2d2_nanopisystem.UDP;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import cc.mvdan.accesspoint.WifiApControl;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import com.bullb.r2d2_nanopisystem.WIFI.WifiService;
import com.bullb.r2d2_nanopisystem.utils.RobotPreference;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class UDPBroadcastService {
    private static UDPBroadcastService udpBroadcastService;
    private AsyncTask asyncTask;
    private String broadcastMsg;
    private Context context;
    private DatagramSocket datagramSocket;
    private Timer udpBroadcastTimer;
    private final String TAG = "UDPBroadcast";
    private final int SERVER_PORT = 8090;

    public static synchronized UDPBroadcastService getInstance(Context context) {
        if (udpBroadcastService == null) {
            udpBroadcastService = new UDPBroadcastService(context);
        }
        return udpBroadcastService;
    }

    private UDPBroadcastService(Context context) {
        this.context = context;
    }

    public void start() {
        stop();
        Log.d("UDPBroadcast", "start");
        String ip = WifiService.getInstance(this.context).getIPAddress();
        boolean isAPMode = WifiService.getInstance(this.context).isAPMode();
        String uuid = RobotPreference.getRobotUdid(this.context);
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("cmd", "updBroadcast");
            jsonObject.put("ip", ip);
            jsonObject.put("uuid", uuid);
            jsonObject.put("name", RobotPreference.getRobotName(this.context));
            jsonObject.put("ap_mode", isAPMode);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.broadcastMsg = jsonObject.toString();
        if (this.datagramSocket != null) {
            this.datagramSocket.close();
        }
        try {
            if (WifiApControl.getInstance(this.context).isEnabled()) {
                this.datagramSocket = new DatagramSocket(8090, InetAddress.getByName(WifiService.AP_IP));
            } else {
                this.datagramSocket = new DatagramSocket();
            }
            this.datagramSocket.setBroadcast(true);
        } catch (SocketException e2) {
            e2.printStackTrace();
        } catch (UnknownHostException e3) {
            e3.printStackTrace();
        }
        if (this.udpBroadcastTimer != null) {
            this.udpBroadcastTimer.cancel();
        }
        this.udpBroadcastTimer = new Timer();
        this.udpBroadcastTimer.schedule(new UDPBroadcastTask(), 0L, 3000L);
    }

    public void stop() {
        Log.d("UDPBroadcast", "stop");
        if (this.asyncTask != null) {
            this.asyncTask.cancel(true);
        }
        if (this.datagramSocket != null) {
            this.datagramSocket.close();
        }
        if (this.udpBroadcastTimer != null) {
            this.udpBroadcastTimer.cancel();
        }
    }

    /* JADX WARN: Type inference failed for: r4v3, types: [com.bullb.r2d2_nanopisystem.UDP.UDPBroadcastService$1] */
    public void sendBroadcast(String msg) {
        try {
            InetAddress inetAddress = WifiService.getInstance(this.context).getBroadcastAddress();
            byte[] message = msg.getBytes();
            final DatagramPacket packet = new DatagramPacket(message, message.length, inetAddress, 8090);
            this.asyncTask = new AsyncTask<Void, Void, Void>() { // from class: com.bullb.r2d2_nanopisystem.UDP.UDPBroadcastService.1
                /* JADX INFO: Access modifiers changed from: protected */
                @Override // android.os.AsyncTask
                public Void doInBackground(Void... params) {
                    try {
                        synchronized (UDPBroadcastService.this.datagramSocket) {
                            UDPBroadcastService.this.datagramSocket.send(packet);
                        }
                        synchronized (UDPBroadcastService.this.datagramSocket) {
                            UDPBroadcastService.this.datagramSocket.send(packet);
                        }
                        return null;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }.execute(new Void[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reconnect() {
        stop();
        start();
    }

    private class UDPBroadcastTask extends TimerTask {
        private final String TAG;

        private UDPBroadcastTask() {
            this.TAG = "UDPBoardcastTimer";
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            if (UDPBroadcastService.this.datagramSocket == null || UDPBroadcastService.this.datagramSocket.isClosed()) {
                if (WifiService.getInstance(UDPBroadcastService.this.context).isAPMode() || WifiService.getInstance(UDPBroadcastService.this.context).getNetworkState() == NetworkInfo.DetailedState.CONNECTED) {
                    UDPBroadcastService.this.reconnect();
                    return;
                }
                return;
            }
            Log.i("UDPBoardcastTimer", "UDPBoardcastTimer triggered");
            if (ModeController.getInstance(UDPBroadcastService.this.context).getMode() == 3) {
                try {
                    JSONObject jsonObject = new JSONObject(UDPBroadcastService.this.broadcastMsg);
                    jsonObject.put("key", ModeController.getInstance(UDPBroadcastService.this.context).getPairKey());
                    UDPBroadcastService.this.broadcastMsg = jsonObject.toString();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            UDPBroadcastService.this.sendBroadcast(UDPBroadcastService.this.broadcastMsg);
            Log.i("UDPBoardcastTimer", "UDPBoardcast sent");
        }
    }
}
