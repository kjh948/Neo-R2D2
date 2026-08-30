package com.koushikdutta.async.http;

/* JADX INFO: loaded from: classes.dex */
public class ConnectionClosedException extends Exception {
    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}
