package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;

/* JADX INFO: loaded from: classes.dex */
public interface AsyncHttpResponse extends DataEmitter {
    int code();

    AsyncSocket detachSocket();

    AsyncHttpRequest getRequest();

    Headers headers();

    String message();

    String protocol();
}
