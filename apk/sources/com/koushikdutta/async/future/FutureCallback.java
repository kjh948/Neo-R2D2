package com.koushikdutta.async.future;

/* JADX INFO: loaded from: classes.dex */
public interface FutureCallback<T> {
    void onCompleted(Exception exc, T t);
}
