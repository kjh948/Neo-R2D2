package com.koushikdutta.async.callback;

import com.koushikdutta.async.AsyncSocket;

/* JADX INFO: loaded from: classes.dex */
public interface ConnectCallback {
    void onConnectCompleted(Exception exc, AsyncSocket asyncSocket);
}
