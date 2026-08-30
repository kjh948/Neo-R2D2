package com.koushikdutta.async.future;

import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class MultiFuture<T> extends SimpleFuture<T> {
    final FutureCallback<T> callback = new FutureCallback<T>() { // from class: com.koushikdutta.async.future.MultiFuture.1
        @Override // com.koushikdutta.async.future.FutureCallback
        public void onCompleted(Exception e, T result) {
            ArrayList<FutureCallback<T>> callbacks;
            synchronized (MultiFuture.this) {
                callbacks = MultiFuture.this.callbacks;
                MultiFuture.this.callbacks = null;
            }
            if (callbacks != null) {
                for (FutureCallback<T> cb : callbacks) {
                    cb.onCompleted(e, result);
                }
            }
        }
    };
    ArrayList<FutureCallback<T>> callbacks;

    @Override // com.koushikdutta.async.future.SimpleFuture, com.koushikdutta.async.future.Future
    public MultiFuture<T> setCallback(FutureCallback<T> callback) {
        synchronized (this) {
            if (this.callbacks == null) {
                this.callbacks = new ArrayList<>();
            }
            this.callbacks.add(callback);
        }
        super.setCallback((FutureCallback) this.callback);
        return this;
    }
}
