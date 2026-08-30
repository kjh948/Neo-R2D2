package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.parser.JSONObjectParser;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class JSONObjectBody implements AsyncHttpRequestBody<JSONObject> {
    public static final String CONTENT_TYPE = "application/json";
    JSONObject json;
    byte[] mBodyBytes;

    public JSONObjectBody() {
    }

    public JSONObjectBody(JSONObject json) {
        this();
        this.json = json;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        new JSONObjectParser().parse(emitter).setCallback(new FutureCallback<JSONObject>() { // from class: com.koushikdutta.async.http.body.JSONObjectBody.1
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, JSONObject result) {
                JSONObjectBody.this.json = result;
                completed.onCompleted(e);
            }
        });
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        Util.writeAll(sink, this.mBodyBytes, completed);
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        return "application/json";
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        this.mBodyBytes = this.json.toString().getBytes();
        return this.mBodyBytes.length;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public JSONObject get() {
        return this.json;
    }
}
