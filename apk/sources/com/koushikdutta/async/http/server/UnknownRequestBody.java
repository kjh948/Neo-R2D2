package com.koushikdutta.async.http.server;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;

/* JADX INFO: loaded from: classes.dex */
public class UnknownRequestBody implements AsyncHttpRequestBody<Void> {
    DataEmitter emitter;
    int length;
    private String mContentType;

    public UnknownRequestBody(String contentType) {
        this.length = -1;
        this.mContentType = contentType;
    }

    public UnknownRequestBody(DataEmitter emitter, String contentType, int length) {
        this.length = -1;
        this.mContentType = contentType;
        this.emitter = emitter;
        this.length = length;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        Util.pump(this.emitter, sink, completed);
        if (this.emitter.isPaused()) {
            this.emitter.resume();
        }
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        return this.mContentType;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        return false;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        return this.length;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public Void get() {
        return null;
    }

    @Deprecated
    public void setCallbacks(DataCallback callback, CompletedCallback endCallback) {
        this.emitter.setEndCallback(endCallback);
        this.emitter.setDataCallback(callback);
    }

    public DataEmitter getEmitter() {
        return this.emitter;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        this.emitter = emitter;
        emitter.setEndCallback(completed);
        emitter.setDataCallback(new DataCallback.NullDataCallback());
    }
}
