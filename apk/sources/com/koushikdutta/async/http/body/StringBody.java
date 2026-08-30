package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.parser.StringParser;

/* JADX INFO: loaded from: classes.dex */
public class StringBody implements AsyncHttpRequestBody<String> {
    public static final String CONTENT_TYPE = "text/plain";
    byte[] mBodyBytes;
    String string;

    public StringBody() {
    }

    public StringBody(String string) {
        this();
        this.string = string;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        new StringParser().parse(emitter).setCallback(new FutureCallback<String>() { // from class: com.koushikdutta.async.http.body.StringBody.1
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, String result) {
                StringBody.this.string = result;
                completed.onCompleted(e);
            }
        });
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        if (this.mBodyBytes == null) {
            this.mBodyBytes = this.string.getBytes();
        }
        Util.writeAll(sink, this.mBodyBytes, completed);
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        if (this.mBodyBytes == null) {
            this.mBodyBytes = this.string.getBytes();
        }
        return this.mBodyBytes.length;
    }

    public String toString() {
        return this.string;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String get() {
        return toString();
    }
}
