package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;

/* JADX INFO: loaded from: classes.dex */
public interface WebSocket extends AsyncSocket {

    public interface PingCallback {
        void onPingReceived(String str);
    }

    public interface PongCallback {
        void onPongReceived(String str);
    }

    public interface StringCallback {
        void onStringAvailable(String str);
    }

    PongCallback getPongCallback();

    AsyncSocket getSocket();

    StringCallback getStringCallback();

    boolean isBuffering();

    void ping(String str);

    void pong(String str);

    void send(String str);

    void send(byte[] bArr);

    void send(byte[] bArr, int i, int i2);

    void setPingCallback(PingCallback pingCallback);

    void setPongCallback(PongCallback pongCallback);

    void setStringCallback(StringCallback stringCallback);
}
