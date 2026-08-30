package com.koushikdutta.async.http.callback;

import com.koushikdutta.async.http.AsyncHttpResponse;

/* JADX INFO: loaded from: classes.dex */
public interface HttpConnectCallback {
    void onConnectCompleted(Exception exc, AsyncHttpResponse asyncHttpResponse);
}
