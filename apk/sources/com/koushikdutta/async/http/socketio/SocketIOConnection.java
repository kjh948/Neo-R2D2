package com.koushikdutta.async.http.socketio;

import android.net.Uri;
import android.text.TextUtils;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.DependentCancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.socketio.transport.SocketIOTransport;
import com.koushikdutta.async.http.socketio.transport.WebSocketTransport;
import com.koushikdutta.async.http.socketio.transport.XHRPollingTransport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
class SocketIOConnection {
    int ackCount;
    Cancellable connecting;
    int heartbeat;
    AsyncHttpClient httpClient;
    long reconnectDelay;
    SocketIORequest request;
    SocketIOTransport transport;
    ArrayList<SocketIOClient> clients = new ArrayList<>();
    Hashtable<String, Acknowledge> acknowledges = new Hashtable<>();

    private interface SelectCallback {
        void onSelect(SocketIOClient socketIOClient);
    }

    public SocketIOConnection(AsyncHttpClient httpClient, SocketIORequest request) {
        this.httpClient = httpClient;
        this.request = request;
        this.reconnectDelay = this.request.config.reconnectDelay;
    }

    public boolean isConnected() {
        return this.transport != null && this.transport.isConnected();
    }

    public void emitRaw(int type, SocketIOClient client, String message, Acknowledge acknowledge) {
        String ack = "";
        if (acknowledge != null) {
            StringBuilder sbAppend = new StringBuilder().append("");
            int i = this.ackCount;
            this.ackCount = i + 1;
            String id = sbAppend.append(i).toString();
            ack = id + "+";
            this.acknowledges.put(id, acknowledge);
        }
        this.transport.send(String.format(Locale.ENGLISH, "%d:%s:%s:%s", Integer.valueOf(type), ack, client.endpoint, message));
    }

    public void connect(SocketIOClient client) {
        if (!this.clients.contains(client)) {
            this.clients.add(client);
        }
        this.transport.send(String.format(Locale.ENGLISH, "1::%s", client.endpoint));
    }

    public void disconnect(SocketIOClient client) {
        this.clients.remove(client);
        boolean needsEndpointDisconnect = true;
        for (SocketIOClient other : this.clients) {
            if (TextUtils.equals(other.endpoint, client.endpoint) || TextUtils.isEmpty(client.endpoint)) {
                needsEndpointDisconnect = false;
                break;
            }
        }
        SocketIOTransport ts = this.transport;
        if (needsEndpointDisconnect && ts != null) {
            ts.send(String.format(Locale.ENGLISH, "0::%s", client.endpoint));
        }
        if (this.clients.size() <= 0 && ts != null) {
            ts.setStringCallback(null);
            ts.setClosedCallback(null);
            ts.disconnect();
            this.transport = null;
        }
    }

    void reconnect(DependentCancellable child) {
        if (!isConnected()) {
            if (this.connecting != null && !this.connecting.isDone() && !this.connecting.isCancelled()) {
                if (child != null) {
                    child.setParent(this.connecting);
                }
            } else {
                this.request.logi("Reconnecting socket.io");
                this.connecting = ((AnonymousClass2) this.httpClient.executeString(this.request, null).then(new TransformFuture<SocketIOTransport, String>() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.2
                    /* JADX INFO: Access modifiers changed from: protected */
                    @Override // com.koushikdutta.async.future.TransformFuture
                    public void transform(String result) throws Exception {
                        String[] parts = result.split(":");
                        final String sessionId = parts[0];
                        if (!"".equals(parts[1])) {
                            SocketIOConnection.this.heartbeat = (Integer.parseInt(parts[1]) / 2) * 1000;
                        } else {
                            SocketIOConnection.this.heartbeat = 0;
                        }
                        String transportsLine = parts[3];
                        String[] transports = transportsLine.split(",");
                        HashSet<String> set = new HashSet<>(Arrays.asList(transports));
                        final SimpleFuture<SocketIOTransport> transport = new SimpleFuture<>();
                        if (set.contains("websocket")) {
                            String sessionUrl = Uri.parse(SocketIOConnection.this.request.getUri().toString()).buildUpon().appendPath("websocket").appendPath(sessionId).build().toString();
                            SocketIOConnection.this.httpClient.websocket(sessionUrl, (String) null, (AsyncHttpClient.WebSocketConnectCallback) null).setCallback(new FutureCallback<WebSocket>() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.2.1
                                @Override // com.koushikdutta.async.future.FutureCallback
                                public void onCompleted(Exception e, WebSocket result2) {
                                    if (e != null) {
                                        transport.setComplete(e);
                                    } else {
                                        transport.setComplete(new WebSocketTransport(result2, sessionId));
                                    }
                                }
                            });
                        } else if (set.contains("xhr-polling")) {
                            String sessionUrl2 = Uri.parse(SocketIOConnection.this.request.getUri().toString()).buildUpon().appendPath("xhr-polling").appendPath(sessionId).build().toString();
                            XHRPollingTransport xhrPolling = new XHRPollingTransport(SocketIOConnection.this.httpClient, sessionUrl2, sessionId);
                            transport.setComplete(xhrPolling);
                        } else {
                            throw new SocketIOException("transport not supported");
                        }
                        setComplete((Future) transport);
                    }
                })).setCallback((FutureCallback) new FutureCallback<SocketIOTransport>() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.1
                    @Override // com.koushikdutta.async.future.FutureCallback
                    public void onCompleted(Exception e, SocketIOTransport result) {
                        if (e != null) {
                            SocketIOConnection.this.reportDisconnect(e);
                            return;
                        }
                        SocketIOConnection.this.reconnectDelay = SocketIOConnection.this.request.config.reconnectDelay;
                        SocketIOConnection.this.transport = result;
                        SocketIOConnection.this.attach();
                    }
                });
                if (child != null) {
                    child.setParent(this.connecting);
                }
            }
        }
    }

    void setupHeartbeat() {
        Runnable heartbeatRunner = new Runnable() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.3
            @Override // java.lang.Runnable
            public void run() {
                SocketIOTransport ts = SocketIOConnection.this.transport;
                if (SocketIOConnection.this.heartbeat > 0 && ts != null && ts.isConnected()) {
                    ts.send("2:::");
                    ts.getServer().postDelayed(this, SocketIOConnection.this.heartbeat);
                }
            }
        };
        heartbeatRunner.run();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void select(String endpoint, SelectCallback callback) {
        for (SocketIOClient client : this.clients) {
            if (endpoint == null || TextUtils.equals(client.endpoint, endpoint)) {
                callback.onSelect(client);
            }
        }
    }

    private void delayReconnect() {
        if (this.transport == null && this.clients.size() != 0) {
            boolean disconnected = false;
            Iterator<SocketIOClient> it = this.clients.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                SocketIOClient client = it.next();
                if (client.disconnected) {
                    disconnected = true;
                    break;
                }
            }
            if (disconnected) {
                this.httpClient.getServer().postDelayed(new Runnable() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.4
                    @Override // java.lang.Runnable
                    public void run() {
                        SocketIOConnection.this.reconnect(null);
                    }
                }, nextReconnectDelay(this.reconnectDelay));
                this.reconnectDelay *= 2;
                if (this.request.config.reconnectDelayMax > 0) {
                    this.reconnectDelay = Math.min(this.reconnectDelay, this.request.config.reconnectDelayMax);
                }
            }
        }
    }

    private long nextReconnectDelay(long targetDelay) {
        return (targetDelay < 2 || targetDelay > 4611686018427387903L || !this.request.config.randomizeReconnectDelay) ? targetDelay : (targetDelay >> 1) + ((long) (targetDelay * Math.random()));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportDisconnect(final Exception ex) {
        if (ex != null) {
            this.request.loge("socket.io disconnected", ex);
        } else {
            this.request.logi("socket.io disconnected");
        }
        select(null, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.5
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                if (client.connected) {
                    client.disconnected = true;
                    DisconnectCallback closed = client.getDisconnectCallback();
                    if (closed != null) {
                        closed.onDisconnect(ex);
                        return;
                    }
                    return;
                }
                ConnectCallback callback = client.connectCallback;
                if (callback != null) {
                    callback.onConnectCompleted(ex, client);
                }
            }
        });
        delayReconnect();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportConnect(String endpoint) {
        select(endpoint, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.6
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                if (!client.isConnected()) {
                    if (!client.connected) {
                        client.connected = true;
                        ConnectCallback callback = client.connectCallback;
                        if (callback != null) {
                            callback.onConnectCompleted(null, client);
                            return;
                        }
                        return;
                    }
                    if (client.disconnected) {
                        client.disconnected = false;
                        ReconnectCallback callback2 = client.reconnectCallback;
                        if (callback2 != null) {
                            callback2.onReconnect();
                        }
                    }
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportJson(String endpoint, final JSONObject jsonMessage, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.7
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                JSONCallback callback = client.jsonCallback;
                if (callback != null) {
                    callback.onJSON(jsonMessage, acknowledge);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportString(String endpoint, final String string, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.8
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                StringCallback callback = client.stringCallback;
                if (callback != null) {
                    callback.onString(string, acknowledge);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportEvent(String endpoint, final String event, final JSONArray arguments, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.9
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                client.onEvent(event, arguments, acknowledge);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportError(String endpoint, final String error) {
        select(endpoint, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.10
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                ErrorCallback callback = client.errorCallback;
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Acknowledge acknowledge(String _messageId, final String endpoint) {
        if (TextUtils.isEmpty(_messageId)) {
            return null;
        }
        final String messageId = _messageId.replaceAll("\\+$", "");
        return new Acknowledge() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.11
            @Override // com.koushikdutta.async.http.socketio.Acknowledge
            public void acknowledge(JSONArray arguments) {
                String data = arguments != null ? "+" + arguments.toString() : "";
                SocketIOTransport transport = SocketIOConnection.this.transport;
                if (transport == null) {
                    final Exception e = new SocketIOException("not connected to server");
                    SocketIOConnection.this.select(endpoint, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.11.1
                        @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
                        public void onSelect(SocketIOClient client) {
                            ExceptionCallback callback = client.exceptionCallback;
                            if (callback != null) {
                                callback.onException(e);
                            }
                        }
                    });
                } else {
                    transport.send(String.format(Locale.ENGLISH, "6:::%s%s", messageId, data));
                }
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void attach() {
        if (this.transport.heartbeats()) {
            setupHeartbeat();
        }
        this.transport.setClosedCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.12
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                SocketIOConnection.this.transport = null;
                SocketIOConnection.this.reportDisconnect(ex);
            }
        });
        this.transport.setStringCallback(new SocketIOTransport.StringCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.13
            @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport.StringCallback
            public void onStringAvailable(String message) {
                try {
                    String[] parts = message.split(":", 4);
                    int code = Integer.parseInt(parts[0]);
                    switch (code) {
                        case 0:
                            SocketIOConnection.this.transport.disconnect();
                            SocketIOConnection.this.reportDisconnect(null);
                            return;
                        case 1:
                            SocketIOConnection.this.reportConnect(parts[2]);
                            return;
                        case 2:
                            SocketIOConnection.this.transport.send("2::");
                            return;
                        case 3:
                            SocketIOConnection.this.reportString(parts[2], parts[3], SocketIOConnection.this.acknowledge(parts[1], parts[2]));
                            return;
                        case 4:
                            String dataString = parts[3];
                            JSONObject jsonMessage = new JSONObject(dataString);
                            SocketIOConnection.this.reportJson(parts[2], jsonMessage, SocketIOConnection.this.acknowledge(parts[1], parts[2]));
                            return;
                        case 5:
                            String dataString2 = parts[3];
                            JSONObject data = new JSONObject(dataString2);
                            String event = data.getString("name");
                            JSONArray args = data.optJSONArray("args");
                            SocketIOConnection.this.reportEvent(parts[2], event, args, SocketIOConnection.this.acknowledge(parts[1], parts[2]));
                            return;
                        case 6:
                            String[] ackParts = parts[3].split("\\+", 2);
                            Acknowledge ack = SocketIOConnection.this.acknowledges.remove(ackParts[0]);
                            if (ack != null) {
                                JSONArray arguments = null;
                                if (ackParts.length == 2) {
                                    arguments = new JSONArray(ackParts[1]);
                                }
                                ack.acknowledge(arguments);
                                return;
                            }
                            return;
                        case 7:
                            SocketIOConnection.this.reportError(parts[2], parts[3]);
                            return;
                        case 8:
                            return;
                        default:
                            throw new SocketIOException("unknown code");
                    }
                } catch (Exception ex) {
                    SocketIOConnection.this.transport.setClosedCallback(null);
                    SocketIOConnection.this.transport.disconnect();
                    SocketIOConnection.this.transport = null;
                    SocketIOConnection.this.reportDisconnect(ex);
                }
            }
        });
        select(null, new SelectCallback() { // from class: com.koushikdutta.async.http.socketio.SocketIOConnection.14
            @Override // com.koushikdutta.async.http.socketio.SocketIOConnection.SelectCallback
            public void onSelect(SocketIOClient client) {
                if (!TextUtils.isEmpty(client.endpoint)) {
                    SocketIOConnection.this.connect(client);
                }
            }
        });
    }
}
