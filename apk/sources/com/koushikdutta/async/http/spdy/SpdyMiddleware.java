package com.koushikdutta.async.http.spdy;

import android.net.Uri;
import android.text.TextUtils;
import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.MultiFuture;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncSSLEngineConfigurator;
import com.koushikdutta.async.http.AsyncSSLSocketMiddleware;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.spdy.AsyncSpdyConnection;
import com.koushikdutta.async.util.Charsets;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/* JADX INFO: loaded from: classes.dex */
public class SpdyMiddleware extends AsyncSSLSocketMiddleware {
    private static final NoSpdyException NO_SPDY = new NoSpdyException();
    Field alpnProtocols;
    Hashtable<String, SpdyConnectionWaiter> connections;
    boolean initialized;
    Method nativeGetAlpnNegotiatedProtocol;
    Method nativeGetNpnNegotiatedProtocol;
    Field npnProtocols;
    Field peerHost;
    Field peerPort;
    boolean spdyEnabled;
    Field sslNativePointer;
    Field sslParameters;
    Field useSni;

    public SpdyMiddleware(AsyncHttpClient client) {
        super(client);
        this.connections = new Hashtable<>();
        addEngineConfigurator(new AsyncSSLEngineConfigurator() { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.1
            @Override // com.koushikdutta.async.http.AsyncSSLEngineConfigurator
            public SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort) {
                return null;
            }

            @Override // com.koushikdutta.async.http.AsyncSSLEngineConfigurator
            public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
                SpdyMiddleware.this.configure(engine, data, host, port);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void configure(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
        if (!this.initialized && this.spdyEnabled) {
            this.initialized = true;
            try {
                this.peerHost = engine.getClass().getSuperclass().getDeclaredField("peerHost");
                this.peerPort = engine.getClass().getSuperclass().getDeclaredField("peerPort");
                this.sslParameters = engine.getClass().getDeclaredField("sslParameters");
                this.npnProtocols = this.sslParameters.getType().getDeclaredField("npnProtocols");
                this.alpnProtocols = this.sslParameters.getType().getDeclaredField("alpnProtocols");
                this.useSni = this.sslParameters.getType().getDeclaredField("useSni");
                this.sslNativePointer = engine.getClass().getDeclaredField("sslNativePointer");
                String nativeCryptoName = this.sslParameters.getType().getPackage().getName() + ".NativeCrypto";
                this.nativeGetNpnNegotiatedProtocol = Class.forName(nativeCryptoName, true, this.sslParameters.getType().getClassLoader()).getDeclaredMethod("SSL_get_npn_negotiated_protocol", Long.TYPE);
                this.nativeGetAlpnNegotiatedProtocol = Class.forName(nativeCryptoName, true, this.sslParameters.getType().getClassLoader()).getDeclaredMethod("SSL_get0_alpn_selected", Long.TYPE);
                this.peerHost.setAccessible(true);
                this.peerPort.setAccessible(true);
                this.sslParameters.setAccessible(true);
                this.npnProtocols.setAccessible(true);
                this.alpnProtocols.setAccessible(true);
                this.useSni.setAccessible(true);
                this.sslNativePointer.setAccessible(true);
                this.nativeGetNpnNegotiatedProtocol.setAccessible(true);
                this.nativeGetAlpnNegotiatedProtocol.setAccessible(true);
            } catch (Exception e) {
                this.sslParameters = null;
                this.npnProtocols = null;
                this.alpnProtocols = null;
                this.useSni = null;
                this.sslNativePointer = null;
                this.nativeGetNpnNegotiatedProtocol = null;
                this.nativeGetAlpnNegotiatedProtocol = null;
            }
        }
        if (canSpdyRequest(data) && this.sslParameters != null) {
            try {
                byte[] protocols = concatLengthPrefixed(Protocol.SPDY_3);
                this.peerHost.set(engine, host);
                this.peerPort.set(engine, Integer.valueOf(port));
                Object sslp = this.sslParameters.get(engine);
                this.alpnProtocols.set(sslp, protocols);
                this.useSni.set(sslp, true);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    private static class SpdyConnectionWaiter extends MultiFuture<AsyncSpdyConnection> {
        SimpleCancellable originalCancellable;

        private SpdyConnectionWaiter() {
            this.originalCancellable = new SimpleCancellable();
        }
    }

    public boolean getSpdyEnabled() {
        return this.spdyEnabled;
    }

    public void setSpdyEnabled(boolean enabled) {
        this.spdyEnabled = enabled;
    }

    @Override // com.koushikdutta.async.http.AsyncSSLSocketMiddleware
    public void setSSLContext(SSLContext sslContext) {
        super.setSSLContext(sslContext);
        this.initialized = false;
    }

    static byte[] concatLengthPrefixed(Protocol... protocols) {
        ByteBuffer result = ByteBuffer.allocate(8192);
        for (Protocol protocol : protocols) {
            if (protocol != Protocol.HTTP_1_0) {
                result.put((byte) protocol.toString().length());
                result.put(protocol.toString().getBytes(Charsets.UTF_8));
            }
        }
        result.flip();
        byte[] ret = new ByteBufferList(result).getAllByteArray();
        return ret;
    }

    private static String requestPath(Uri uri) {
        String pathAndQuery = uri.getEncodedPath();
        if (pathAndQuery == null) {
            pathAndQuery = "/";
        } else if (!pathAndQuery.startsWith("/")) {
            pathAndQuery = "/" + pathAndQuery;
        }
        if (!TextUtils.isEmpty(uri.getEncodedQuery())) {
            return pathAndQuery + "?" + uri.getEncodedQuery();
        }
        return pathAndQuery;
    }

    private static class NoSpdyException extends Exception {
        private NoSpdyException() {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void noSpdy(String key) {
        SpdyConnectionWaiter conn = this.connections.remove(key);
        if (conn != null) {
            conn.setComplete((Exception) NO_SPDY);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void invokeConnect(String key, ConnectCallback callback, Exception e, AsyncSSLSocket socket) {
        SpdyConnectionWaiter waiter = this.connections.get(key);
        if (waiter == null || waiter.originalCancellable.setComplete()) {
            callback.onConnectCompleted(e, socket);
        }
    }

    @Override // com.koushikdutta.async.http.AsyncSSLSocketMiddleware
    protected AsyncSSLSocketWrapper.HandshakeCallback createHandshakeCallback(final AsyncHttpClientMiddleware.GetSocketData data, final ConnectCallback callback) {
        final String key = (String) data.state.get("spdykey");
        return key == null ? super.createHandshakeCallback(data, callback) : new AsyncSSLSocketWrapper.HandshakeCallback() { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.2
            @Override // com.koushikdutta.async.AsyncSSLSocketWrapper.HandshakeCallback
            public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket) {
                String protoString;
                Protocol p;
                data.request.logv("checking spdy handshake");
                if (e != null || SpdyMiddleware.this.nativeGetAlpnNegotiatedProtocol == null) {
                    SpdyMiddleware.this.invokeConnect(key, callback, e, socket);
                    SpdyMiddleware.this.noSpdy(key);
                    return;
                }
                try {
                    long ptr = ((Long) SpdyMiddleware.this.sslNativePointer.get(socket.getSSLEngine())).longValue();
                    byte[] proto = (byte[]) SpdyMiddleware.this.nativeGetAlpnNegotiatedProtocol.invoke(null, Long.valueOf(ptr));
                    if (proto == null || (p = Protocol.get((protoString = new String(proto)))) == null || !p.needsSpdyConnection()) {
                        SpdyMiddleware.this.invokeConnect(key, callback, null, socket);
                        SpdyMiddleware.this.noSpdy(key);
                    } else {
                        AsyncSpdyConnection connection = new AsyncSpdyConnection(socket, Protocol.get(protoString)) { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.2.1
                            boolean hasReceivedSettings;

                            @Override // com.koushikdutta.async.http.spdy.AsyncSpdyConnection, com.koushikdutta.async.http.spdy.FrameReader.Handler
                            public void settings(boolean clearPrevious, Settings settings) {
                                super.settings(clearPrevious, settings);
                                if (!this.hasReceivedSettings) {
                                    this.hasReceivedSettings = true;
                                    SpdyConnectionWaiter waiter = SpdyMiddleware.this.connections.get(key);
                                    if (waiter.originalCancellable.setComplete()) {
                                        data.request.logv("using new spdy connection for host: " + data.request.getUri().getHost());
                                        SpdyMiddleware.this.newSocket(data, this, callback);
                                    }
                                    waiter.setComplete(this);
                                }
                            }
                        };
                        try {
                            connection.sendConnectionPreface();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void newSocket(AsyncHttpClientMiddleware.GetSocketData data, AsyncSpdyConnection connection, ConnectCallback callback) {
        AsyncHttpRequest request = data.request;
        data.protocol = connection.protocol.toString();
        AsyncHttpRequestBody requestBody = data.request.getBody();
        ArrayList<Header> headers = new ArrayList<>();
        headers.add(new Header(Header.TARGET_METHOD, request.getMethod()));
        headers.add(new Header(Header.TARGET_PATH, requestPath(request.getUri())));
        String host = request.getHeaders().get("Host");
        if (Protocol.SPDY_3 == connection.protocol) {
            headers.add(new Header(Header.VERSION, "HTTP/1.1"));
            headers.add(new Header(Header.TARGET_HOST, host));
        } else if (Protocol.HTTP_2 == connection.protocol) {
            headers.add(new Header(Header.TARGET_AUTHORITY, host));
        } else {
            throw new AssertionError();
        }
        headers.add(new Header(Header.TARGET_SCHEME, request.getUri().getScheme()));
        Multimap mm = request.getHeaders().getMultiMap();
        for (String key : mm.keySet()) {
            if (!SpdyTransport.isProhibitedHeader(connection.protocol, key)) {
                for (String value : (List) mm.get(key)) {
                    headers.add(new Header(key.toLowerCase(Locale.US), value));
                }
            }
        }
        request.logv("\n" + request);
        AsyncSpdyConnection.SpdySocket spdy = connection.newStream(headers, requestBody != null, true);
        callback.onConnectCompleted(null, spdy);
    }

    private boolean canSpdyRequest(AsyncHttpClientMiddleware.GetSocketData data) {
        return data.request.getBody() == null;
    }

    @Override // com.koushikdutta.async.http.AsyncSSLSocketMiddleware, com.koushikdutta.async.http.AsyncSocketMiddleware
    protected ConnectCallback wrapCallback(AsyncHttpClientMiddleware.GetSocketData data, Uri uri, int port, boolean proxied, ConnectCallback callback) {
        final ConnectCallback superCallback = super.wrapCallback(data, uri, port, proxied, callback);
        final String key = (String) data.state.get("spdykey");
        return key == null ? superCallback : new ConnectCallback() { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.3
            @Override // com.koushikdutta.async.callback.ConnectCallback
            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                SpdyConnectionWaiter conn;
                if (ex != null && (conn = SpdyMiddleware.this.connections.remove(key)) != null) {
                    conn.setComplete(ex);
                }
                superCallback.onConnectCompleted(ex, socket);
            }
        };
    }

    @Override // com.koushikdutta.async.http.AsyncSocketMiddleware, com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public Cancellable getSocket(final AsyncHttpClientMiddleware.GetSocketData data) {
        Uri uri = data.request.getUri();
        int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }
        if (!this.spdyEnabled) {
            return super.getSocket(data);
        }
        if (!canSpdyRequest(data)) {
            return super.getSocket(data);
        }
        String key = uri.getHost() + port;
        SpdyConnectionWaiter conn = this.connections.get(key);
        if (conn != null) {
            if (conn.tryGetException() instanceof NoSpdyException) {
                return super.getSocket(data);
            }
            if (conn.tryGet() != null && !conn.tryGet().socket.isOpen()) {
                this.connections.remove(key);
                conn = null;
            }
        }
        if (conn == null) {
            data.state.put("spdykey", key);
            Cancellable ret = super.getSocket(data);
            if (!ret.isDone() && !ret.isCancelled()) {
                SpdyConnectionWaiter conn2 = new SpdyConnectionWaiter();
                this.connections.put(key, conn2);
                return conn2.originalCancellable;
            }
            return ret;
        }
        data.request.logv("waiting for potential spdy connection for host: " + data.request.getUri().getHost());
        final SimpleCancellable ret2 = new SimpleCancellable();
        conn.setCallback((FutureCallback) new FutureCallback<AsyncSpdyConnection>() { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.4
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, AsyncSpdyConnection conn3) {
                if (e instanceof NoSpdyException) {
                    data.request.logv("spdy not available");
                    ret2.setParent(SpdyMiddleware.super.getSocket(data));
                } else if (e != null) {
                    if (ret2.setComplete()) {
                        data.connectCallback.onConnectCompleted(e, null);
                    }
                } else {
                    data.request.logv("using existing spdy connection for host: " + data.request.getUri().getHost());
                    if (ret2.setComplete()) {
                        SpdyMiddleware.this.newSocket(data, conn3, data.connectCallback);
                    }
                }
            }
        });
        return ret2;
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public boolean exchangeHeaders(final AsyncHttpClientMiddleware.OnExchangeHeaderData data) {
        if (!(data.socket instanceof AsyncSpdyConnection.SpdySocket)) {
            return super.exchangeHeaders(data);
        }
        AsyncHttpRequestBody requestBody = data.request.getBody();
        if (requestBody != null) {
            data.response.sink(data.socket);
        }
        data.sendHeadersCallback.onCompleted(null);
        final AsyncSpdyConnection.SpdySocket spdySocket = (AsyncSpdyConnection.SpdySocket) data.socket;
        ((AnonymousClass6) spdySocket.headers().then(new TransformFuture<Headers, List<Header>>() { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.6
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // com.koushikdutta.async.future.TransformFuture
            public void transform(List<Header> result) throws Exception {
                Headers headers = new Headers();
                for (Header header : result) {
                    String key = header.name.utf8();
                    String value = header.value.utf8();
                    headers.add(key, value);
                }
                String status = headers.remove(Header.RESPONSE_STATUS.utf8());
                String[] statusParts = status.split(" ", 2);
                data.response.code(Integer.parseInt(statusParts[0]));
                if (statusParts.length == 2) {
                    data.response.message(statusParts[1]);
                }
                data.response.protocol(headers.remove(Header.VERSION.utf8()));
                data.response.headers(headers);
                setComplete(headers);
            }
        })).setCallback((FutureCallback) new FutureCallback<Headers>() { // from class: com.koushikdutta.async.http.spdy.SpdyMiddleware.5
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, Headers result) {
                data.receiveHeadersCallback.onCompleted(e);
                DataEmitter emitter = HttpUtil.getBodyDecoder(spdySocket, spdySocket.getConnection().protocol, result, false);
                data.response.emitter(emitter);
            }
        });
        return true;
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onRequestSent(AsyncHttpClientMiddleware.OnRequestSentData data) {
        if ((data.socket instanceof AsyncSpdyConnection.SpdySocket) && data.request.getBody() != null) {
            data.response.sink().end();
        }
    }
}
