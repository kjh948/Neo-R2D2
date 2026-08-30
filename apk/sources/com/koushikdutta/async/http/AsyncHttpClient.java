package com.koushikdutta.async.http;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import com.koushikdutta.async.AsyncSSLException;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.koushikdutta.async.http.callback.RequestCallback;
import com.koushikdutta.async.http.spdy.SpdyMiddleware;
import com.koushikdutta.async.parser.AsyncParser;
import com.koushikdutta.async.parser.ByteBufferListParser;
import com.koushikdutta.async.parser.JSONArrayParser;
import com.koushikdutta.async.parser.JSONObjectParser;
import com.koushikdutta.async.parser.StringParser;
import com.koushikdutta.async.stream.OutputStreamDataCallback;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class AsyncHttpClient {
    static final /* synthetic */ boolean $assertionsDisabled;
    private static final String LOGTAG = "AsyncHttp";
    private static AsyncHttpClient mDefaultInstance;
    HttpTransportMiddleware httpTransportMiddleware;
    final List<AsyncHttpClientMiddleware> mMiddleware = new CopyOnWriteArrayList();
    AsyncServer mServer;
    AsyncSocketMiddleware socketMiddleware;
    SpdyMiddleware sslSocketMiddleware;

    public static abstract class DownloadCallback extends RequestCallbackBase<ByteBufferList> {
    }

    public static abstract class FileCallback extends RequestCallbackBase<File> {
    }

    public static abstract class JSONArrayCallback extends RequestCallbackBase<JSONArray> {
    }

    public static abstract class JSONObjectCallback extends RequestCallbackBase<JSONObject> {
    }

    public static abstract class StringCallback extends RequestCallbackBase<String> {
    }

    public interface WebSocketConnectCallback {
        void onCompleted(Exception exc, WebSocket webSocket);
    }

    static {
        $assertionsDisabled = !AsyncHttpClient.class.desiredAssertionStatus();
    }

    public static AsyncHttpClient getDefaultInstance() {
        if (mDefaultInstance == null) {
            mDefaultInstance = new AsyncHttpClient(AsyncServer.getDefault());
        }
        return mDefaultInstance;
    }

    public Collection<AsyncHttpClientMiddleware> getMiddleware() {
        return this.mMiddleware;
    }

    public void insertMiddleware(AsyncHttpClientMiddleware middleware) {
        this.mMiddleware.add(0, middleware);
    }

    public AsyncHttpClient(AsyncServer server) {
        this.mServer = server;
        AsyncSocketMiddleware asyncSocketMiddleware = new AsyncSocketMiddleware(this);
        this.socketMiddleware = asyncSocketMiddleware;
        insertMiddleware(asyncSocketMiddleware);
        SpdyMiddleware spdyMiddleware = new SpdyMiddleware(this);
        this.sslSocketMiddleware = spdyMiddleware;
        insertMiddleware(spdyMiddleware);
        HttpTransportMiddleware httpTransportMiddleware = new HttpTransportMiddleware();
        this.httpTransportMiddleware = httpTransportMiddleware;
        insertMiddleware(httpTransportMiddleware);
        this.sslSocketMiddleware.addEngineConfigurator(new SSLEngineSNIConfigurator());
    }

    /* JADX INFO: Access modifiers changed from: private */
    @SuppressLint({"NewApi"})
    public static void setupAndroidProxy(AsyncHttpRequest request) {
        String proxyHost;
        if (request.proxyHost == null) {
            try {
                List<Proxy> proxies = ProxySelector.getDefault().select(URI.create(request.getUri().toString()));
                if (!proxies.isEmpty()) {
                    Proxy proxy = proxies.get(0);
                    if (proxy.type() == Proxy.Type.HTTP && (proxy.address() instanceof InetSocketAddress)) {
                        InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
                        if (Build.VERSION.SDK_INT >= 14) {
                            proxyHost = proxyAddress.getHostString();
                        } else {
                            InetAddress address = proxyAddress.getAddress();
                            if (address != null) {
                                proxyHost = address.getHostAddress();
                            } else {
                                proxyHost = proxyAddress.getHostName();
                            }
                        }
                        request.enableProxy(proxyHost, proxyAddress.getPort());
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    public AsyncSocketMiddleware getSocketMiddleware() {
        return this.socketMiddleware;
    }

    public SpdyMiddleware getSSLSocketMiddleware() {
        return this.sslSocketMiddleware;
    }

    public Future<AsyncHttpResponse> execute(AsyncHttpRequest request, HttpConnectCallback callback) {
        FutureAsyncHttpResponse ret = new FutureAsyncHttpResponse();
        execute(request, 0, ret, callback);
        return ret;
    }

    public Future<AsyncHttpResponse> execute(String uri, HttpConnectCallback callback) {
        return execute(new AsyncHttpGet(uri), callback);
    }

    private class FutureAsyncHttpResponse extends SimpleFuture<AsyncHttpResponse> {
        public Object scheduled;
        public AsyncSocket socket;
        public Runnable timeoutRunnable;

        private FutureAsyncHttpResponse() {
        }

        @Override // com.koushikdutta.async.future.SimpleFuture, com.koushikdutta.async.future.SimpleCancellable, com.koushikdutta.async.future.Cancellable
        public boolean cancel() {
            if (!super.cancel()) {
                return false;
            }
            if (this.socket != null) {
                this.socket.setDataCallback(new DataCallback.NullDataCallback());
                this.socket.close();
            }
            if (this.scheduled != null) {
                AsyncHttpClient.this.mServer.removeAllCallbacks(this.scheduled);
            }
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void reportConnectedCompleted(FutureAsyncHttpResponse cancel, Exception ex, AsyncHttpResponseImpl response, AsyncHttpRequest request, HttpConnectCallback callback) {
        boolean complete;
        if (!$assertionsDisabled && callback == null) {
            throw new AssertionError();
        }
        this.mServer.removeAllCallbacks(cancel.scheduled);
        if (ex != null) {
            request.loge("Connection error", ex);
            complete = cancel.setComplete(ex);
        } else {
            request.logd("Connection successful");
            complete = cancel.setComplete(response);
        }
        if (complete) {
            callback.onConnectCompleted(ex, response);
            if (!$assertionsDisabled && ex == null && response.socket() != null && response.getDataCallback() == null && !response.isPaused()) {
                throw new AssertionError();
            }
            return;
        }
        if (response != null) {
            response.setDataCallback(new DataCallback.NullDataCallback());
            response.close();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void execute(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        if (this.mServer.isAffinityThread()) {
            executeAffinity(request, redirectCount, cancel, callback);
        } else {
            this.mServer.post(new Runnable() { // from class: com.koushikdutta.async.http.AsyncHttpClient.1
                @Override // java.lang.Runnable
                public void run() {
                    AsyncHttpClient.this.executeAffinity(request, redirectCount, cancel, callback);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static long getTimeoutRemaining(AsyncHttpRequest request) {
        return request.getTimeout();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void copyHeader(AsyncHttpRequest from, AsyncHttpRequest to, String header) {
        String value = from.getHeaders().get(header);
        if (!TextUtils.isEmpty(value)) {
            to.getHeaders().set(header, value);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void executeAffinity(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback) {
        if (!$assertionsDisabled && !this.mServer.isAffinityThread()) {
            throw new AssertionError();
        }
        if (redirectCount > 15) {
            reportConnectedCompleted(cancel, new RedirectLimitExceededException("too many redirects"), null, request, callback);
            return;
        }
        request.getUri();
        final AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data = new AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData();
        request.executionTime = System.currentTimeMillis();
        data.request = request;
        request.logd("Executing request.");
        for (AsyncHttpClientMiddleware middleware : this.mMiddleware) {
            middleware.onRequest(data);
        }
        if (request.getTimeout() > 0) {
            cancel.timeoutRunnable = new Runnable() { // from class: com.koushikdutta.async.http.AsyncHttpClient.2
                @Override // java.lang.Runnable
                public void run() {
                    if (data.socketCancellable != null) {
                        data.socketCancellable.cancel();
                        if (data.socket != null) {
                            data.socket.close();
                        }
                    }
                    AsyncHttpClient.this.reportConnectedCompleted(cancel, new TimeoutException(), null, request, callback);
                }
            };
            cancel.scheduled = this.mServer.postDelayed(cancel.timeoutRunnable, getTimeoutRemaining(request));
        }
        data.connectCallback = new ConnectCallback() { // from class: com.koushikdutta.async.http.AsyncHttpClient.3
            boolean reported;

            @Override // com.koushikdutta.async.callback.ConnectCallback
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                if (this.reported && socket != null) {
                    socket.setDataCallback(new DataCallback.NullDataCallback());
                    socket.setEndCallback(new CompletedCallback.NullCompletedCallback());
                    socket.close();
                    throw new AssertionError("double connect callback");
                }
                this.reported = true;
                request.logv("socket connected");
                if (cancel.isCancelled()) {
                    if (socket != null) {
                        socket.close();
                        return;
                    }
                    return;
                }
                if (cancel.timeoutRunnable != null) {
                    AsyncHttpClient.this.mServer.removeAllCallbacks(cancel.scheduled);
                }
                if (ex != null) {
                    AsyncHttpClient.this.reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }
                data.socket = socket;
                cancel.socket = socket;
                AsyncHttpClient.this.executeSocket(request, redirectCount, cancel, callback, data);
            }
        };
        setupAndroidProxy(request);
        if (request.getBody() != null && request.getHeaders().get("Content-Type") == null) {
            request.getHeaders().set("Content-Type", request.getBody().getContentType());
        }
        for (AsyncHttpClientMiddleware middleware2 : this.mMiddleware) {
            Cancellable socketCancellable = middleware2.getSocket(data);
            if (socketCancellable != null) {
                data.socketCancellable = socketCancellable;
                cancel.setParent(socketCancellable);
                return;
            }
        }
        Exception unsupportedURI = new IllegalArgumentException("invalid uri=" + request.getUri() + " middlewares=" + this.mMiddleware);
        reportConnectedCompleted(cancel, unsupportedURI, null, request, callback);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void executeSocket(final AsyncHttpRequest request, final int redirectCount, final FutureAsyncHttpResponse cancel, final HttpConnectCallback callback, final AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data) {
        final AsyncHttpResponseImpl ret = new AsyncHttpResponseImpl(request) { // from class: com.koushikdutta.async.http.AsyncHttpClient.4
            @Override // com.koushikdutta.async.http.AsyncHttpResponseImpl
            protected void onRequestCompleted(Exception ex) {
                if (ex != null) {
                    AsyncHttpClient.this.reportConnectedCompleted(cancel, ex, null, request, callback);
                    return;
                }
                request.logv("request completed");
                if (!cancel.isCancelled()) {
                    if (cancel.timeoutRunnable != null && this.mHeaders == null) {
                        AsyncHttpClient.this.mServer.removeAllCallbacks(cancel.scheduled);
                        cancel.scheduled = AsyncHttpClient.this.mServer.postDelayed(cancel.timeoutRunnable, AsyncHttpClient.getTimeoutRemaining(request));
                    }
                    for (AsyncHttpClientMiddleware middleware : AsyncHttpClient.this.mMiddleware) {
                        middleware.onRequestSent(data);
                    }
                }
            }

            @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataTrackingEmitter
            public void setDataEmitter(DataEmitter emitter) {
                data.bodyEmitter = emitter;
                for (AsyncHttpClientMiddleware middleware : AsyncHttpClient.this.mMiddleware) {
                    middleware.onBodyDecoder(data);
                }
                super.setDataEmitter(data.bodyEmitter);
                Headers headers = this.mHeaders;
                int responseCode = code();
                if ((responseCode == 301 || responseCode == 302 || responseCode == 307) && request.getFollowRedirect()) {
                    String location = headers.get("Location");
                    try {
                        Uri redirect = Uri.parse(location);
                        if (redirect.getScheme() == null) {
                            redirect = Uri.parse(new URL(new URL(request.getUri().toString()), location).toString());
                        }
                        String method = request.getMethod().equals(AsyncHttpHead.METHOD) ? AsyncHttpHead.METHOD : AsyncHttpGet.METHOD;
                        AsyncHttpRequest newReq = new AsyncHttpRequest(redirect, method);
                        newReq.executionTime = request.executionTime;
                        newReq.logLevel = request.logLevel;
                        newReq.LOGTAG = request.LOGTAG;
                        newReq.proxyHost = request.proxyHost;
                        newReq.proxyPort = request.proxyPort;
                        AsyncHttpClient.setupAndroidProxy(newReq);
                        AsyncHttpClient.copyHeader(request, newReq, "User-Agent");
                        AsyncHttpClient.copyHeader(request, newReq, "Range");
                        request.logi("Redirecting");
                        newReq.logi("Redirected");
                        AsyncHttpClient.this.execute(newReq, redirectCount + 1, cancel, callback);
                        setDataCallback(new DataCallback.NullDataCallback());
                        return;
                    } catch (Exception e) {
                        AsyncHttpClient.this.reportConnectedCompleted(cancel, e, this, request, callback);
                        return;
                    }
                }
                request.logv("Final (post cache response) headers:\n" + toString());
                AsyncHttpClient.this.reportConnectedCompleted(cancel, null, this, request, callback);
            }

            @Override // com.koushikdutta.async.http.AsyncHttpResponseImpl
            protected void onHeadersReceived() {
                super.onHeadersReceived();
                if (!cancel.isCancelled()) {
                    if (cancel.timeoutRunnable != null) {
                        AsyncHttpClient.this.mServer.removeAllCallbacks(cancel.scheduled);
                    }
                    request.logv("Received headers:\n" + toString());
                    for (AsyncHttpClientMiddleware middleware : AsyncHttpClient.this.mMiddleware) {
                        middleware.onHeadersReceived(data);
                    }
                }
            }

            @Override // com.koushikdutta.async.http.AsyncHttpResponseImpl, com.koushikdutta.async.DataEmitterBase
            protected void report(Exception ex) {
                if (ex != null) {
                    request.loge("exception during response", ex);
                }
                if (!cancel.isCancelled()) {
                    if (ex instanceof AsyncSSLException) {
                        request.loge("SSL Exception", ex);
                        AsyncSSLException ase = (AsyncSSLException) ex;
                        request.onHandshakeException(ase);
                        if (ase.getIgnore()) {
                            return;
                        }
                    }
                    AsyncSocket socket = socket();
                    if (socket != null) {
                        super.report(ex);
                        if ((!socket.isOpen() || ex != null) && headers() == null && ex != null) {
                            AsyncHttpClient.this.reportConnectedCompleted(cancel, ex, null, request, callback);
                        }
                        data.exception = ex;
                        for (AsyncHttpClientMiddleware middleware : AsyncHttpClient.this.mMiddleware) {
                            middleware.onResponseComplete(data);
                        }
                    }
                }
            }

            @Override // com.koushikdutta.async.http.AsyncHttpResponse
            public AsyncSocket detachSocket() {
                request.logd("Detaching socket");
                AsyncSocket socket = socket();
                if (socket == null) {
                    return null;
                }
                socket.setWriteableCallback(null);
                socket.setClosedCallback(null);
                socket.setEndCallback(null);
                socket.setDataCallback(null);
                setSocket(null);
                return socket;
            }
        };
        data.sendHeadersCallback = new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncHttpClient.5
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    ret.report(ex);
                } else {
                    ret.onHeadersSent();
                }
            }
        };
        data.receiveHeadersCallback = new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncHttpClient.6
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    ret.report(ex);
                } else {
                    ret.onHeadersReceived();
                }
            }
        };
        data.response = ret;
        ret.setSocket(data.socket);
        for (AsyncHttpClientMiddleware middleware : this.mMiddleware) {
            if (middleware.exchangeHeaders(data)) {
                return;
            }
        }
    }

    public static abstract class RequestCallbackBase<T> implements RequestCallback<T> {
        @Override // com.koushikdutta.async.http.callback.RequestCallback
        public void onProgress(AsyncHttpResponse response, long downloaded, long total) {
        }

        @Override // com.koushikdutta.async.http.callback.RequestCallback
        public void onConnect(AsyncHttpResponse response) {
        }
    }

    public Future<ByteBufferList> executeByteBufferList(AsyncHttpRequest request, DownloadCallback callback) {
        return execute(request, new ByteBufferListParser(), callback);
    }

    public Future<String> executeString(AsyncHttpRequest req, StringCallback callback) {
        return execute(req, new StringParser(), callback);
    }

    public Future<JSONObject> executeJSONObject(AsyncHttpRequest req, JSONObjectCallback callback) {
        return execute(req, new JSONObjectParser(), callback);
    }

    public Future<JSONArray> executeJSONArray(AsyncHttpRequest req, JSONArrayCallback callback) {
        return execute(req, new JSONArrayParser(), callback);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public <T> void invokeWithAffinity(RequestCallback<T> callback, SimpleFuture<T> future, AsyncHttpResponse response, Exception e, T result) {
        boolean complete;
        if (e != null) {
            complete = future.setComplete(e);
        } else {
            complete = future.setComplete(result);
        }
        if (complete && callback != null) {
            callback.onCompleted(e, response, result);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public <T> void invoke(final RequestCallback<T> callback, final SimpleFuture<T> future, final AsyncHttpResponse response, final Exception e, final T result) {
        Runnable runnable = new Runnable() { // from class: com.koushikdutta.async.http.AsyncHttpClient.7
            @Override // java.lang.Runnable
            public void run() {
                AsyncHttpClient.this.invokeWithAffinity(callback, future, response, e, result);
            }
        };
        this.mServer.post(runnable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void invokeProgress(RequestCallback callback, AsyncHttpResponse response, long downloaded, long total) {
        if (callback != null) {
            callback.onProgress(response, downloaded, total);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void invokeConnect(RequestCallback callback, AsyncHttpResponse response) {
        if (callback != null) {
            callback.onConnect(response);
        }
    }

    /* JADX WARN: Generic types in debug info not equals: java.lang.Object != com.koushikdutta.async.future.SimpleFuture<java.io.File> */
    public Future<File> executeFile(AsyncHttpRequest req, String filename, FileCallback callback) {
        final File file = new File(filename);
        file.getParentFile().mkdirs();
        try {
            final OutputStream fout = new BufferedOutputStream(new FileOutputStream(file), 8192);
            final FutureAsyncHttpResponse cancel = new FutureAsyncHttpResponse();
            SimpleFuture<File> ret = new SimpleFuture<File>() { // from class: com.koushikdutta.async.http.AsyncHttpClient.8
                @Override // com.koushikdutta.async.future.SimpleCancellable
                public void cancelCleanup() {
                    try {
                        cancel.get().setDataCallback(new DataCallback.NullDataCallback());
                        cancel.get().close();
                    } catch (Exception e) {
                    }
                    try {
                        fout.close();
                    } catch (Exception e2) {
                    }
                    file.delete();
                }
            };
            ret.setParent((Cancellable) cancel);
            execute(req, 0, cancel, new AnonymousClass9(fout, file, callback, ret));
            return ret;
        } catch (FileNotFoundException e) {
            SimpleFuture<File> ret2 = new SimpleFuture<>();
            ret2.setComplete(e);
            return ret2;
        }
    }

    /* JADX INFO: renamed from: com.koushikdutta.async.http.AsyncHttpClient$9, reason: invalid class name */
    class AnonymousClass9 implements HttpConnectCallback {
        long mDownloaded = 0;
        final /* synthetic */ FileCallback val$callback;
        final /* synthetic */ File val$file;
        final /* synthetic */ OutputStream val$fout;
        final /* synthetic */ SimpleFuture val$ret;

        AnonymousClass9(OutputStream outputStream, File file, FileCallback fileCallback, SimpleFuture simpleFuture) {
            this.val$fout = outputStream;
            this.val$file = file;
            this.val$callback = fileCallback;
            this.val$ret = simpleFuture;
        }

        @Override // com.koushikdutta.async.http.callback.HttpConnectCallback
        public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
            if (ex == null) {
                AsyncHttpClient.this.invokeConnect(this.val$callback, response);
                final long contentLength = HttpUtil.contentLength(response.headers());
                response.setDataCallback(new OutputStreamDataCallback(this.val$fout) { // from class: com.koushikdutta.async.http.AsyncHttpClient.9.1
                    @Override // com.koushikdutta.async.stream.OutputStreamDataCallback, com.koushikdutta.async.callback.DataCallback
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        AnonymousClass9.this.mDownloaded += (long) bb.remaining();
                        super.onDataAvailable(emitter, bb);
                        AsyncHttpClient.this.invokeProgress(AnonymousClass9.this.val$callback, response, AnonymousClass9.this.mDownloaded, contentLength);
                    }
                });
                response.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncHttpClient.9.2
                    @Override // com.koushikdutta.async.callback.CompletedCallback
                    public void onCompleted(Exception ex2) {
                        try {
                            AnonymousClass9.this.val$fout.close();
                        } catch (IOException e) {
                            ex2 = e;
                        }
                        if (ex2 == null) {
                            AsyncHttpClient.this.invoke(AnonymousClass9.this.val$callback, AnonymousClass9.this.val$ret, response, null, AnonymousClass9.this.val$file);
                        } else {
                            AnonymousClass9.this.val$file.delete();
                            AsyncHttpClient.this.invoke(AnonymousClass9.this.val$callback, AnonymousClass9.this.val$ret, response, ex2, null);
                        }
                    }
                });
                return;
            }
            try {
                this.val$fout.close();
            } catch (IOException e) {
            }
            this.val$file.delete();
            AsyncHttpClient.this.invoke(this.val$callback, this.val$ret, response, ex, null);
        }
    }

    public <T> SimpleFuture<T> execute(AsyncHttpRequest req, final AsyncParser<T> parser, final RequestCallback<T> callback) {
        FutureAsyncHttpResponse cancel = new FutureAsyncHttpResponse();
        final SimpleFuture<T> ret = new SimpleFuture<>();
        execute(req, 0, cancel, new HttpConnectCallback() { // from class: com.koushikdutta.async.http.AsyncHttpClient.10
            @Override // com.koushikdutta.async.http.callback.HttpConnectCallback
            public void onConnectCompleted(Exception ex, final AsyncHttpResponse response) {
                if (ex != null) {
                    AsyncHttpClient.this.invoke(callback, ret, response, ex, null);
                    return;
                }
                AsyncHttpClient.this.invokeConnect(callback, response);
                ret.setParent((Cancellable) parser.parse(response).setCallback(new FutureCallback<T>() { // from class: com.koushikdutta.async.http.AsyncHttpClient.10.1
                    @Override // com.koushikdutta.async.future.FutureCallback
                    public void onCompleted(Exception e, T result) {
                        AsyncHttpClient.this.invoke(callback, ret, response, e, result);
                    }
                }));
            }
        });
        ret.setParent((Cancellable) cancel);
        return ret;
    }

    public Future<WebSocket> websocket(final AsyncHttpRequest req, String protocol, final WebSocketConnectCallback callback) {
        WebSocketImpl.addWebSocketUpgradeHeaders(req, protocol);
        final SimpleFuture<WebSocket> ret = new SimpleFuture<>();
        Cancellable connect = execute(req, new HttpConnectCallback() { // from class: com.koushikdutta.async.http.AsyncHttpClient.11
            @Override // com.koushikdutta.async.http.callback.HttpConnectCallback
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    if (ret.setComplete(ex) && callback != null) {
                        callback.onCompleted(ex, null);
                        return;
                    }
                    return;
                }
                WebSocket ws = WebSocketImpl.finishHandshake(req.getHeaders(), response);
                if (ws == null) {
                    ex = new WebSocketHandshakeException("Unable to complete websocket handshake");
                    if (!ret.setComplete(ex)) {
                        return;
                    }
                } else if (!ret.setComplete(ws)) {
                    return;
                }
                if (callback != null) {
                    callback.onCompleted(ex, ws);
                }
            }
        });
        ret.setParent(connect);
        return ret;
    }

    public Future<WebSocket> websocket(String uri, String protocol, WebSocketConnectCallback callback) {
        AsyncHttpGet get = new AsyncHttpGet(uri.replace("ws://", "http://").replace("wss://", "https://"));
        return websocket(get, protocol, callback);
    }

    public AsyncServer getServer() {
        return this.mServer;
    }
}
