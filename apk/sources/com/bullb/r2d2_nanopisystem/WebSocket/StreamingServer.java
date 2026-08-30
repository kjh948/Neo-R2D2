package com.bullb.r2d2_nanopisystem.WebSocket;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.CentralController;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class StreamingServer extends WebSocketServer {
    public static final String TAG = "StreamingServer";
    private static final int TIMEOUT_SECONDS = 5;
    private static final int WEB_SOCKET_PORT = 12121;
    private static StreamingServer socketServer;
    private final boolean LOG;
    private Context context;
    private WebSocket streamingSocket;

    public static synchronized StreamingServer getInstance(Context context) throws UnknownHostException {
        if (socketServer == null) {
            socketServer = new StreamingServer(context);
        }
        return socketServer;
    }

    public StreamingServer(Context context) throws UnknownHostException {
        super(new InetSocketAddress(WEB_SOCKET_PORT));
        this.LOG = true;
        this.context = context;
        setConnectionLostTimeout(5);
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        InetAddress addr = conn.getRemoteSocketAddress().getAddress();
        Log.d(TAG, addr.getHostAddress() + "entered the room!");
        if (this.streamingSocket != null) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("cmd", "streaming");
                jsonObject.put("resultCode", 421);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Already Exist");
            conn.send(jsonObject.toString() + "\n");
            conn.close();
            return;
        }
        Log.d(TAG, "Success");
        this.streamingSocket = conn;
        conn.send("enter video socket");
        ((Activity) this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.WebSocket.StreamingServer.1
            @Override // java.lang.Runnable
            public void run() {
                CentralController centralController = CentralController.getInstance(StreamingServer.this.context);
                centralController.startVideoStreaming();
                centralController.stopFaceDetection();
            }
        });
        ModeController.getInstance(this.context).stopSleepTimer();
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, conn + " has left the streaming room!");
        if (conn.equals(this.streamingSocket)) {
            this.streamingSocket = null;
            CentralController centralController = CentralController.getInstance(this.context);
            centralController.startFaceDetection();
            centralController.stopVideoStreaming();
            ModeController.getInstance(this.context).restartSleepTimer();
        }
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onMessage(final WebSocket conn, final String message) {
        ((Activity) this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.WebSocket.StreamingServer.2
            @Override // java.lang.Runnable
            public void run() {
                Log.d(StreamingServer.TAG, conn + ": " + message);
            }
        });
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onError(WebSocket conn, Exception ex) {
        Log.d(TAG, conn + ": " + ex);
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onStart() {
        Log.d(TAG, "Server started!");
    }

    public void startServer() {
        try {
            socketServer.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        if (this.streamingSocket != null) {
            this.streamingSocket.close();
        }
        this.streamingSocket = null;
    }

    public void send(String data) {
        synchronized (this) {
            this.streamingSocket.send(data);
        }
    }

    public void send(byte[] data) {
        synchronized (this) {
            try {
            } catch (WebsocketNotConnectedException e) {
                e.printStackTrace();
            }
            if (this.streamingSocket != null) {
                this.streamingSocket.send(data);
            }
        }
    }
}
