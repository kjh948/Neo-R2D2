package com.koushikdutta.async.http;

import android.net.Uri;
import android.text.TextUtils;
import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

/* JADX INFO: loaded from: classes.dex */
public class AsyncSSLSocketMiddleware extends AsyncSocketMiddleware {
    protected List<AsyncSSLEngineConfigurator> engineConfigurators;
    protected HostnameVerifier hostnameVerifier;
    protected SSLContext sslContext;
    protected TrustManager[] trustManagers;

    public AsyncSSLSocketMiddleware(AsyncHttpClient client) {
        super(client, "https", org.java_websocket.WebSocket.DEFAULT_WSS_PORT);
        this.engineConfigurators = new ArrayList();
    }

    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext getSSLContext() {
        return this.sslContext != null ? this.sslContext : AsyncSSLSocketWrapper.getDefaultSSLContext();
    }

    public void setTrustManagers(TrustManager[] trustManagers) {
        this.trustManagers = trustManagers;
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public void addEngineConfigurator(AsyncSSLEngineConfigurator engineConfigurator) {
        this.engineConfigurators.add(engineConfigurator);
    }

    public void clearEngineConfigurators() {
        this.engineConfigurators.clear();
    }

    protected SSLEngine createConfiguredSSLEngine(AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
        SSLContext sslContext = getSSLContext();
        SSLEngine sslEngine = null;
        for (AsyncSSLEngineConfigurator configurator : this.engineConfigurators) {
            sslEngine = configurator.createEngine(sslContext, host, port);
            if (sslEngine != null) {
                break;
            }
        }
        for (AsyncSSLEngineConfigurator configurator2 : this.engineConfigurators) {
            configurator2.configureEngine(sslEngine, data, host, port);
        }
        return sslEngine;
    }

    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(AsyncHttpClientMiddleware.GetSocketData data, final ConnectCallback callback) {
        return new AsyncSSLSocketWrapper.HandshakeCallback() { // from class: com.koushikdutta.async.http.AsyncSSLSocketMiddleware.1
            @Override // com.koushikdutta.async.AsyncSSLSocketWrapper.HandshakeCallback
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                callback.onConnectCompleted(e, socket);
            }
        };
    }

    protected void tryHandshake(AsyncSocket socket, AsyncHttpClientMiddleware.GetSocketData data, Uri uri, int port, ConnectCallback callback) {
        AsyncSSLSocketWrapper.handshake(socket, uri.getHost(), port, createConfiguredSSLEngine(data, uri.getHost(), port), this.trustManagers, this.hostnameVerifier, true, createHandshakeCallback(data, callback));
    }

    /* JADX INFO: renamed from: com.koushikdutta.async.http.AsyncSSLSocketMiddleware$2, reason: invalid class name */
    class AnonymousClass2 implements ConnectCallback {
        final /* synthetic */ ConnectCallback val$callback;
        final /* synthetic */ AsyncHttpClientMiddleware.GetSocketData val$data;
        final /* synthetic */ int val$port;
        final /* synthetic */ boolean val$proxied;
        final /* synthetic */ Uri val$uri;

        AnonymousClass2(ConnectCallback connectCallback, boolean z, AsyncHttpClientMiddleware.GetSocketData getSocketData, Uri uri, int i) {
            this.val$callback = connectCallback;
            this.val$proxied = z;
            this.val$data = getSocketData;
            this.val$uri = uri;
            this.val$port = i;
        }

        @Override // com.koushikdutta.async.callback.ConnectCallback
        public void onConnectCompleted(Exception ex, final AsyncSocket socket) {
            if (ex != null) {
                this.val$callback.onConnectCompleted(ex, socket);
            } else {
                if (!this.val$proxied) {
                    AsyncSSLSocketMiddleware.this.tryHandshake(socket, this.val$data, this.val$uri, this.val$port, this.val$callback);
                    return;
                }
                String connect = String.format(Locale.ENGLISH, "CONNECT %s:%s HTTP/1.1\r\nHost: %s\r\n\r\n", this.val$uri.getHost(), Integer.valueOf(this.val$port), this.val$uri.getHost());
                this.val$data.request.logv("Proxying: " + connect);
                Util.writeAll(socket, connect.getBytes(), new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncSSLSocketMiddleware.2.1
                    @Override // com.koushikdutta.async.callback.CompletedCallback
                    public void onCompleted(Exception ex2) {
                        if (ex2 != null) {
                            AnonymousClass2.this.val$callback.onConnectCompleted(ex2, socket);
                            return;
                        }
                        LineEmitter liner = new LineEmitter();
                        liner.setLineCallback(new LineEmitter.StringCallback() { // from class: com.koushikdutta.async.http.AsyncSSLSocketMiddleware.2.1.1
                            String statusLine;

                            @Override // com.koushikdutta.async.LineEmitter.StringCallback
                            public void onStringAvailable(String s) {
                                AnonymousClass2.this.val$data.request.logv(s);
                                if (this.statusLine == null) {
                                    this.statusLine = s.trim();
                                    if (!this.statusLine.matches("HTTP/1.\\d 2\\d\\d .*")) {
                                        socket.setDataCallback(null);
                                        socket.setEndCallback(null);
                                        AnonymousClass2.this.val$callback.onConnectCompleted(new IOException("non 2xx status line: " + this.statusLine), socket);
                                        return;
                                    }
                                    return;
                                }
                                if (TextUtils.isEmpty(s.trim())) {
                                    socket.setDataCallback(null);
                                    socket.setEndCallback(null);
                                    AsyncSSLSocketMiddleware.this.tryHandshake(socket, AnonymousClass2.this.val$data, AnonymousClass2.this.val$uri, AnonymousClass2.this.val$port, AnonymousClass2.this.val$callback);
                                }
                            }
                        });
                        socket.setDataCallback(liner);
                        socket.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncSSLSocketMiddleware.2.1.2
                            @Override // com.koushikdutta.async.callback.CompletedCallback
                            public void onCompleted(Exception ex3) {
                                if (!socket.isOpen() && ex3 == null) {
                                    ex3 = new IOException("socket closed before proxy connect response");
                                }
                                AnonymousClass2.this.val$callback.onConnectCompleted(ex3, socket);
                            }
                        });
                    }
                });
            }
        }
    }

    @Override // com.koushikdutta.async.http.AsyncSocketMiddleware
    protected ConnectCallback wrapCallback(AsyncHttpClientMiddleware.GetSocketData data, Uri uri, int port, boolean proxied, ConnectCallback callback) {
        return new AnonymousClass2(callback, proxied, data, uri, port);
    }
}
