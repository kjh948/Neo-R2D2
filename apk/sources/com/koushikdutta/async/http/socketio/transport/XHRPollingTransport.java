package com.koushikdutta.async.http.socketio.transport;

import android.net.Uri;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.socketio.transport.SocketIOTransport;

/* JADX INFO: loaded from: classes.dex */
public class XHRPollingTransport implements SocketIOTransport {
    private static final String SEPARATOR = "�";
    private AsyncHttpClient client;
    private CompletedCallback closedCallback;
    private boolean connected;
    private String sessionId;
    private Uri sessionUrl;
    private SocketIOTransport.StringCallback stringCallback;

    public XHRPollingTransport(AsyncHttpClient client, String sessionUrl, String sessionId) {
        this.client = client;
        this.sessionUrl = Uri.parse(sessionUrl);
        this.sessionId = sessionId;
        doLongPolling();
        this.connected = true;
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public boolean isConnected() {
        return this.connected;
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void setClosedCallback(CompletedCallback handler) {
        this.closedCallback = handler;
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void disconnect() {
        this.connected = false;
        close(null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void close(Exception ex) {
        if (this.closedCallback != null) {
            this.closedCallback.onCompleted(ex);
        }
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public AsyncServer getServer() {
        return this.client.getServer();
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void send(String message) {
        if (message.startsWith("5")) {
            postMessage(message);
            return;
        }
        AsyncHttpRequest request = new AsyncHttpPost(computedRequestUrl());
        request.setBody(new StringBody(message));
        this.client.executeString(request, new AsyncHttpClient.StringCallback() { // from class: com.koushikdutta.async.http.socketio.transport.XHRPollingTransport.1
            @Override // com.koushikdutta.async.callback.ResultCallback
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    XHRPollingTransport.this.close(e);
                } else {
                    XHRPollingTransport.this.sendResult(result);
                }
            }
        });
    }

    private void postMessage(String message) {
        if (message.startsWith("5")) {
            AsyncHttpRequest request = new AsyncHttpPost(computedRequestUrl());
            request.setBody(new StringBody(message));
            this.client.executeString(request, null);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void doLongPolling() {
        this.client.executeString(new AsyncHttpGet(computedRequestUrl()), new AsyncHttpClient.StringCallback() { // from class: com.koushikdutta.async.http.socketio.transport.XHRPollingTransport.2
            @Override // com.koushikdutta.async.callback.ResultCallback
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    XHRPollingTransport.this.close(e);
                } else {
                    XHRPollingTransport.this.sendResult(result);
                    XHRPollingTransport.this.doLongPolling();
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendResult(String result) {
        if (this.stringCallback != null) {
            if (!result.contains(SEPARATOR)) {
                this.stringCallback.onStringAvailable(result);
                return;
            }
            String[] results = result.split(SEPARATOR);
            for (int i = 1; i < results.length; i += 2) {
                this.stringCallback.onStringAvailable(results[i + 1]);
            }
        }
    }

    private String computedRequestUrl() {
        String currentTime = String.valueOf(System.currentTimeMillis());
        return this.sessionUrl.buildUpon().appendQueryParameter("t", currentTime).build().toString();
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public void setStringCallback(SocketIOTransport.StringCallback callback) {
        this.stringCallback = callback;
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public boolean heartbeats() {
        return false;
    }

    @Override // com.koushikdutta.async.http.socketio.transport.SocketIOTransport
    public String getSessionId() {
        return this.sessionId;
    }
}
