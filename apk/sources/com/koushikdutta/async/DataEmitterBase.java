package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

/* JADX INFO: loaded from: classes.dex */
public abstract class DataEmitterBase implements DataEmitter {
    CompletedCallback endCallback;
    private boolean ended;
    DataCallback mDataCallback;

    protected void report(Exception e) {
        if (!this.ended) {
            this.ended = true;
            if (getEndCallback() != null) {
                getEndCallback().onCompleted(e);
            }
        }
    }

    @Override // com.koushikdutta.async.DataEmitter
    public final void setEndCallback(CompletedCallback callback) {
        this.endCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public final CompletedCallback getEndCallback() {
        return this.endCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void setDataCallback(DataCallback callback) {
        this.mDataCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public DataCallback getDataCallback() {
        return this.mDataCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public String charset() {
        return null;
    }
}
