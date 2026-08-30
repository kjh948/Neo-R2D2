package com.koushikdutta.async.http.server;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import java.util.regex.Matcher;

/* JADX INFO: loaded from: classes.dex */
public abstract class AsyncHttpServerRequestImpl extends FilteredDataEmitter implements AsyncHttpServerRequest, CompletedCallback {
    AsyncHttpRequestBody mBody;
    Matcher mMatcher;
    AsyncSocket mSocket;
    String method;
    private String statusLine;
    private Headers mRawHeaders = new Headers();
    private CompletedCallback mReporter = new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerRequestImpl.1
        @Override // com.koushikdutta.async.callback.CompletedCallback
        public void onCompleted(Exception error) {
            AsyncHttpServerRequestImpl.this.onCompleted(error);
        }
    };
    LineEmitter.StringCallback mHeaderCallback = new LineEmitter.StringCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerRequestImpl.2
        @Override // com.koushikdutta.async.LineEmitter.StringCallback
        public void onStringAvailable(String s) {
            try {
                if (AsyncHttpServerRequestImpl.this.statusLine == null) {
                    AsyncHttpServerRequestImpl.this.statusLine = s;
                    if (!AsyncHttpServerRequestImpl.this.statusLine.contains("HTTP/")) {
                        AsyncHttpServerRequestImpl.this.onNotHttp();
                        AsyncHttpServerRequestImpl.this.mSocket.setDataCallback(null);
                        return;
                    }
                    return;
                }
                if (!"\r".equals(s)) {
                    AsyncHttpServerRequestImpl.this.mRawHeaders.addLine(s);
                    return;
                }
                DataEmitter emitter = HttpUtil.getBodyDecoder(AsyncHttpServerRequestImpl.this.mSocket, Protocol.HTTP_1_1, AsyncHttpServerRequestImpl.this.mRawHeaders, true);
                AsyncHttpServerRequestImpl.this.mBody = HttpUtil.getBody(emitter, AsyncHttpServerRequestImpl.this.mReporter, AsyncHttpServerRequestImpl.this.mRawHeaders);
                if (AsyncHttpServerRequestImpl.this.mBody == null) {
                    AsyncHttpServerRequestImpl.this.mBody = AsyncHttpServerRequestImpl.this.onUnknownBody(AsyncHttpServerRequestImpl.this.mRawHeaders);
                    if (AsyncHttpServerRequestImpl.this.mBody == null) {
                        AsyncHttpServerRequestImpl.this.mBody = new UnknownRequestBody(AsyncHttpServerRequestImpl.this.mRawHeaders.get("Content-Type"));
                    }
                }
                AsyncHttpServerRequestImpl.this.mBody.parse(emitter, AsyncHttpServerRequestImpl.this.mReporter);
                AsyncHttpServerRequestImpl.this.onHeadersReceived();
            } catch (Exception ex) {
                AsyncHttpServerRequestImpl.this.onCompleted(ex);
            }
        }
    };

    protected abstract void onHeadersReceived();

    public String getStatusLine() {
        return this.statusLine;
    }

    public void onCompleted(Exception e) {
        report(e);
    }

    protected void onNotHttp() {
        System.out.println("not http!");
    }

    protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
        return null;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
    public String getMethod() {
        return this.method;
    }

    void setSocket(AsyncSocket socket) {
        this.mSocket = socket;
        LineEmitter liner = new LineEmitter();
        this.mSocket.setDataCallback(liner);
        liner.setLineCallback(this.mHeaderCallback);
        this.mSocket.setEndCallback(new CompletedCallback.NullCompletedCallback());
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
    public AsyncSocket getSocket() {
        return this.mSocket;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
    public Headers getHeaders() {
        return this.mRawHeaders;
    }

    @Override // com.koushikdutta.async.DataEmitterBase, com.koushikdutta.async.DataEmitter
    public void setDataCallback(DataCallback callback) {
        this.mSocket.setDataCallback(callback);
    }

    @Override // com.koushikdutta.async.DataEmitterBase, com.koushikdutta.async.DataEmitter
    public DataCallback getDataCallback() {
        return this.mSocket.getDataCallback();
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
    public boolean isChunked() {
        return this.mSocket.isChunked();
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
    public Matcher getMatcher() {
        return this.mMatcher;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
    public AsyncHttpRequestBody getBody() {
        return this.mBody;
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
    public void pause() {
        this.mSocket.pause();
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
    public void resume() {
        this.mSocket.resume();
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
    public boolean isPaused() {
        return this.mSocket.isPaused();
    }

    public String toString() {
        return this.mRawHeaders == null ? super.toString() : this.mRawHeaders.toPrefixString(this.statusLine);
    }
}
