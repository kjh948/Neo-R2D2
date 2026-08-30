package com.bullb.r2d2_nanopisystem.WebSocket;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.bullb.r2d2_nanopisystem.ModeControl.ModeController;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/* JADX INFO: loaded from: classes.dex */
public class SocketServer extends WebSocketServer {
    public static final String TAG = "SocketServer";
    private static final int TIMEOUT_SECONDS = 5;
    private static final int WEB_SOCKET_PORT = 8887;
    private static SocketServer socketServer;
    private final boolean LOG;
    private ArrayList<SocketConnection> connections;
    private Context context;

    public static synchronized SocketServer getInstance(Context context) throws UnknownHostException {
        if (socketServer == null) {
            socketServer = new SocketServer(context);
        }
        return socketServer;
    }

    public SocketServer(Context context) throws UnknownHostException {
        super(new InetSocketAddress(WEB_SOCKET_PORT));
        this.LOG = true;
        this.connections = new ArrayList<>();
        this.context = context;
        setConnectionLostTimeout(5);
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, conn.getRemoteSocketAddress().getAddress().getHostAddress() + "entered the room!");
        for (SocketConnection c : this.connections) {
            if (c.getWebSocket().getRemoteSocketAddress().getHostName().equals(conn.getRemoteSocketAddress().getHostName())) {
                c.close();
                Log.d(TAG, "Same source connected, closed old Connection");
            }
        }
        this.connections.add(new SocketConnection(this.context, conn, this));
        Log.d(TAG, "connecting size:" + String.valueOf(this.connections.size()));
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, conn + " has left the room!");
        removeFromList(conn);
        Log.d(TAG, "connecting size:" + String.valueOf(this.connections.size()));
        checkControlModeNeeded();
    }

    private void removeFromList(WebSocket conn) {
        SocketConnection connection = getConnectionFromSocket(conn);
        if (connection != null) {
            this.connections.remove(getConnectionFromSocket(conn));
        }
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onMessage(final WebSocket conn, final String message) {
        Log.d(TAG, "onMessage: " + message);
        ((Activity) this.context).runOnUiThread(new Runnable() { // from class: com.bullb.r2d2_nanopisystem.WebSocket.SocketServer.1
            @Override // java.lang.Runnable
            public void run() {
                Log.d(SocketServer.TAG, conn + ": " + message);
                SocketConnection socketConnection = SocketServer.this.getConnectionFromSocket(conn);
                if (socketConnection == null) {
                    conn.close();
                } else {
                    socketConnection.receiveMessage(message);
                }
            }
        });
    }

    public void checkControlModeNeeded() {
        int controllingNum = getControllingNum();
        Log.d(TAG, "Number of user controlling:" + String.valueOf(controllingNum));
        if (controllingNum > 0) {
            ModeController.getInstance(this.context).startUserControlMode();
        } else {
            ModeController.getInstance(this.context).stopUserControlMode();
        }
    }

    public int getControllingNum() {
        int num = 0;
        for (SocketConnection socketConnection : this.connections) {
            if (socketConnection.isControlling()) {
                num++;
            }
        }
        return num;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public SocketConnection getConnectionFromSocket(WebSocket webSocket) {
        for (SocketConnection connection : this.connections) {
            if (connection.getWebSocket().equals(webSocket)) {
                return connection;
            }
        }
        webSocket.close();
        return null;
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onError(WebSocket conn, Exception ex) {
        Log.d(TAG, conn + ": " + ex);
        removeFromList(conn);
        Log.d(TAG, "connecting size:" + String.valueOf(this.connections.size()));
    }

    @Override // org.java_websocket.server.WebSocketServer
    public void onStart() {
        Log.d(TAG, "Server started!");
    }

    public void startServer() {
        clearAll();
        try {
            socketServer.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        clearAll();
    }

    public void closeClient(String uuid) {
        for (SocketConnection connection : this.connections) {
            if (connection.getClientUUID().equals(uuid)) {
                connection.close();
                this.connections.remove(connection);
                return;
            }
        }
    }

    public void clearAll() {
        for (SocketConnection connection : this.connections) {
            connection.close();
        }
        this.connections.clear();
    }

    public void send(String data) {
        for (SocketConnection connection : this.connections) {
            connection.send(data);
        }
    }
}
