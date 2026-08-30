package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import java.nio.charset.Charset;

/* JADX INFO: loaded from: classes.dex */
abstract class AsyncHttpResponseImpl extends FilteredDataEmitter implements AsyncSocket, AsyncHttpResponse, AsyncHttpClientMiddleware.ResponseHead {
    static final /* synthetic */ boolean $assertionsDisabled;
    int code;
    protected Headers mHeaders;
    private AsyncHttpRequest mRequest;
    DataSink mSink;
    private AsyncSocket mSocket;
    String message;
    String protocol;
    private CompletedCallback mReporter = new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncHttpResponseImpl.2
        @Override // com.koushikdutta.async.callback.CompletedCallback
        public void onCompleted(Exception error) {
            if (error != null && !AsyncHttpResponseImpl.this.mCompleted) {
                AsyncHttpResponseImpl.this.report(new ConnectionClosedException("connection closed before response completed.", error));
            } else {
                AsyncHttpResponseImpl.this.report(error);
            }
        }
    };
    boolean mCompleted = false;
    private boolean mFirstWrite = true;

    static {
        $assertionsDisabled = !AsyncHttpResponseImpl.class.desiredAssertionStatus();
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncSocket socket() {
        return this.mSocket;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpResponse
    public AsyncHttpRequest getRequest() {
        return this.mRequest;
    }

    void setSocket(AsyncSocket exchange) {
        this.mSocket = exchange;
        if (this.mSocket != null) {
            this.mSocket.setEndCallback(this.mReporter);
        }
    }

    protected void onHeadersSent() {
        AsyncHttpRequestBody requestBody = this.mRequest.getBody();
        if (requestBody != null) {
            requestBody.write(this.mRequest, this, new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncHttpResponseImpl.1
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    AsyncHttpResponseImpl.this.onRequestCompleted(ex);
                }
            });
        } else {
            onRequestCompleted(null);
        }
    }

    protected void onRequestCompleted(Exception ex) {
    }

    protected void onHeadersReceived() {
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public DataEmitter emitter() {
        return getDataEmitter();
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncHttpClientMiddleware.ResponseHead emitter(DataEmitter emitter) {
        setDataEmitter(emitter);
        return this;
    }

    private void terminate() {
        this.mSocket.setDataCallback(new DataCallback.NullDataCallback() { // from class: com.koushikdutta.async.http.AsyncHttpResponseImpl.3
            @Override // com.koushikdutta.async.callback.DataCallback.NullDataCallback, com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                AsyncHttpResponseImpl.this.mSocket.close();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @Override // com.koushikdutta.async.DataEmitterBase
    public void report(Exception e) {
        super.report(e);
        terminate();
        this.mSocket.setWriteableCallback(null);
        this.mSocket.setClosedCallback(null);
        this.mSocket.setEndCallback(null);
        this.mCompleted = true;
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
    public void close() {
        super.close();
        terminate();
    }

    public AsyncHttpResponseImpl(AsyncHttpRequest request) {
        this.mRequest = request;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpResponse, com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public Headers headers() {
        return this.mHeaders;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncHttpClientMiddleware.ResponseHead headers(Headers headers) {
        this.mHeaders = headers;
        return this;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpResponse, com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public int code() {
        return this.code;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncHttpClientMiddleware.ResponseHead code(int code) {
        this.code = code;
        return this;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncHttpClientMiddleware.ResponseHead protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncHttpClientMiddleware.ResponseHead message(String message) {
        this.message = message;
        return this;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpResponse, com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public String protocol() {
        return this.protocol;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpResponse, com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public String message() {
        return this.message;
    }

    public String toString() {
        return this.mHeaders == null ? super.toString() : this.mHeaders.toPrefixString(this.protocol + " " + this.code + " " + this.message);
    }

    private void assertContent() {
        if (this.mFirstWrite) {
            this.mFirstWrite = false;
            if (!$assertionsDisabled && this.mRequest.getHeaders().get("Content-Type") == null) {
                throw new AssertionError();
            }
            if (!$assertionsDisabled && this.mRequest.getHeaders().get("Transfer-Encoding") == null && HttpUtil.contentLength(this.mRequest.getHeaders()) == -1) {
                throw new AssertionError();
            }
        }
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public DataSink sink() {
        return this.mSink;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware.ResponseHead
    public AsyncHttpClientMiddleware.ResponseHead sink(DataSink sink) {
        this.mSink = sink;
        return this;
    }

    @Override // com.koushikdutta.async.DataSink
    public void write(ByteBufferList bb) {
        assertContent();
        this.mSink.write(bb);
    }

    @Override // com.koushikdutta.async.DataSink
    public void end() {
        throw new AssertionError("end called?");
    }

    @Override // com.koushikdutta.async.DataSink
    public void setWriteableCallback(WritableCallback handler) {
        this.mSink.setWriteableCallback(handler);
    }

    @Override // com.koushikdutta.async.DataSink
    public WritableCallback getWriteableCallback() {
        return this.mSink.getWriteableCallback();
    }

    @Override // com.koushikdutta.async.DataSink
    public boolean isOpen() {
        return this.mSink.isOpen();
    }

    @Override // com.koushikdutta.async.DataSink
    public void setClosedCallback(CompletedCallback handler) {
        this.mSink.setClosedCallback(handler);
    }

    @Override // com.koushikdutta.async.DataSink
    public CompletedCallback getClosedCallback() {
        return this.mSink.getClosedCallback();
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mSocket.getServer();
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitterBase, com.koushikdutta.async.DataEmitter
    public String charset() {
        String cs;
        Multimap mm = Multimap.parseSemicolonDelimited(headers().get("Content-Type"));
        if (mm == null || (cs = mm.getString("charset")) == null || !Charset.isSupported(cs)) {
            return null;
        }
        return cs;
    }
}
