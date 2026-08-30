package com.koushikdutta.async.http.socketio.transport;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.socketio.transport.SocketIOTransport;

/* JADX INFO: loaded from: classes.dex */
public class WebSocketTransport implements SocketIOTransport {
    private String sessionId;
    private SocketIOTransport.StringCallback stringCallback;
    private WebSocket webSocket;

    public WebSocketTransport(WebSocket webSocket, String sessionId) {
        this.webSocket = webSocket;
        this.sessionId = sessionId;
        this.webSocket.setDataCallback(new DataCallback.NullDataCallback());
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public boolean isConnected() {
        return this.webSocket.isOpen();
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void setClosedCallback(CompletedCallback handler) {
        this.webSocket.setClosedCallback(handler);
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void disconnect() {
        this.webSocket.close();
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public AsyncServer getServer() {
        return this.webSocket.getServer();
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void send(String message) {
        this.webSocket.send(message);
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void setStringCallback(final SocketIOTransport.StringCallback callback) {
        if (this.stringCallback != callback) {
            if (callback == null) {
                this.webSocket.setStringCallback(null);
            } else {
                this.webSocket.setStringCallback(new WebSocket.StringCallback() { // from class: com.koushikdutta.async.http.socketio.transport.WebSocketTransport.1
                    @Override // com.koushikdutta.async.http.WebSocket.StringCallback
                    public void onStringAvailable(String s) {
                        callback.onStringAvailable(s);
                    }
                });
            }
            this.stringCallback = callback;
        }
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public boolean heartbeats() {
        return true;
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public String getSessionId() {
        return this.sessionId;
    }
}
