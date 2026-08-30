package com.koushikdutta.async.http;

import android.net.Uri;
import android.support.v7.widget.ActivityChooserView;
import com.koushikdutta.async.ArrayDeque;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ContinuationCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Continuation;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public class AsyncSocketMiddleware extends SimpleMiddleware {
    boolean connectAllAddresses;
    Hashtable<String, ConnectionInfo> connectionInfo;
    int idleTimeoutMs;
    protected AsyncHttpClient mClient;
    int maxConnectionCount;
    int port;
    InetSocketAddress proxyAddress;
    String proxyHost;
    int proxyPort;
    String scheme;

    public AsyncSocketMiddleware(AsyncHttpClient client, String scheme, int port) {
        this.idleTimeoutMs = 300000;
        this.connectionInfo = new Hashtable<>();
        this.maxConnectionCount = ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
        this.mClient = client;
        this.scheme = scheme;
        this.port = port;
    }

    public void setIdleTimeoutMs(int idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public int getSchemePort(Uri uri) {
        if (uri.getScheme() == null || !uri.getScheme().equals(this.scheme)) {
            return -1;
        }
        if (uri.getPort() == -1) {
            return this.port;
        }
        return uri.getPort();
    }

    public AsyncSocketMiddleware(AsyncHttpClient client) {
        this(client, "http", 80);
    }

    protected ConnectCallback wrapCallback(AsyncHttpClientMiddleware.GetSocketData data, Uri uri, int port, boolean proxied, ConnectCallback callback) {
        return callback;
    }

    public boolean getConnectAllAddresses() {
        return this.connectAllAddresses;
    }

    public void setConnectAllAddresses(boolean connectAllAddresses) {
        this.connectAllAddresses = connectAllAddresses;
    }

    public void disableProxy() {
        this.proxyPort = -1;
        this.proxyHost = null;
        this.proxyAddress = null;
    }

    public void enableProxy(String host, int port) {
        this.proxyHost = host;
        this.proxyPort = port;
        this.proxyAddress = null;
    }

    String computeLookup(Uri uri, int port, String proxyHost, int proxyPort) {
        String proxy;
        if (proxyHost != null) {
            proxy = proxyHost + ":" + proxyPort;
        } else {
            proxy = "";
        }
        if (proxyHost != null) {
            proxy = proxyHost + ":" + proxyPort;
        }
        return uri.getScheme() + "//" + uri.getHost() + ":" + port + "?proxy=" + proxy;
    }

    class IdleSocketHolder {
        long idleTime = System.currentTimeMillis();
        AsyncSocket socket;

        public IdleSocketHolder(AsyncSocket socket) {
            this.socket = socket;
        }
    }

    static class ConnectionInfo {
        int openCount;
        ArrayDeque<AsyncHttpClientMiddleware.GetSocketData> queue = new ArrayDeque<>();
        ArrayDeque<IdleSocketHolder> sockets = new ArrayDeque<>();

        ConnectionInfo() {
        }
    }

    public int getMaxConnectionCount() {
        return this.maxConnectionCount;
    }

    public void setMaxConnectionCount(int maxConnectionCount) {
        this.maxConnectionCount = maxConnectionCount;
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public Cancellable getSocket(AsyncHttpClientMiddleware.GetSocketData data) {
        String unresolvedHost;
        int unresolvedPort;
        Uri uri = data.request.getUri();
        int port = getSchemePort(data.request.getUri());
        if (port == -1) {
            return null;
        }
        data.state.put("socket-owner", this);
        String lookup = computeLookup(uri, port, data.request.getProxyHost(), data.request.getProxyPort());
        ConnectionInfo info = getOrCreateConnectionInfo(lookup);
        synchronized (this) {
            if (info.openCount >= this.maxConnectionCount) {
                SimpleCancellable simpleCancellable = new SimpleCancellable();
                info.queue.add(data);
                return simpleCancellable;
            }
            info.openCount++;
            while (!info.sockets.isEmpty()) {
                IdleSocketHolder idleSocketHolder = info.sockets.pop();
                AsyncSocket socket = idleSocketHolder.socket;
                if (idleSocketHolder.idleTime + ((long) this.idleTimeoutMs) < System.currentTimeMillis()) {
                    socket.setClosedCallback(null);
                    socket.close();
                } else if (socket.isOpen()) {
                    data.request.logd("Reusing keep-alive socket");
                    data.connectCallback.onConnectCompleted(null, socket);
                    SimpleCancellable ret = new SimpleCancellable();
                    ret.setComplete();
                    return ret;
                }
            }
            if (!this.connectAllAddresses || this.proxyHost != null || data.request.getProxyHost() != null) {
                data.request.logd("Connecting socket");
                boolean proxied = false;
                if (data.request.getProxyHost() == null && this.proxyHost != null) {
                    data.request.enableProxy(this.proxyHost, this.proxyPort);
                }
                if (data.request.getProxyHost() != null) {
                    unresolvedHost = data.request.getProxyHost();
                    unresolvedPort = data.request.getProxyPort();
                    proxied = true;
                } else {
                    unresolvedHost = uri.getHost();
                    unresolvedPort = port;
                }
                if (proxied) {
                    data.request.logv("Using proxy: " + unresolvedHost + ":" + unresolvedPort);
                }
                return this.mClient.getServer().connectSocket(unresolvedHost, unresolvedPort, wrapCallback(data, uri, port, proxied, data.connectCallback));
            }
            data.request.logv("Resolving domain and connecting to all available addresses");
            return (Cancellable) this.mClient.getServer().getAllByName(uri.getHost()).then(new AnonymousClass1(data, uri, port));
        }
    }

    /* JADX INFO: renamed from: com.koushikdutta.async.http.AsyncSocketMiddleware$1, reason: invalid class name */
    class AnonymousClass1 extends TransformFuture<AsyncSocket, InetAddress[]> {
        Exception lastException;
        final /* synthetic */ AsyncHttpClientMiddleware.GetSocketData val$data;
        final /* synthetic */ int val$port;
        final /* synthetic */ Uri val$uri;

        AnonymousClass1(AsyncHttpClientMiddleware.GetSocketData getSocketData, Uri uri, int i) {
            this.val$data = getSocketData;
            this.val$uri = uri;
            this.val$port = i;
        }

        @Override // com.koushikdutta.async.future.TransformFuture
        protected void error(Exception e) {
            super.error(e);
            AsyncSocketMiddleware.this.wrapCallback(this.val$data, this.val$uri, this.val$port, false, this.val$data.connectCallback).onConnectCompleted(e, null);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.koushikdutta.async.future.TransformFuture
        public void transform(InetAddress[] result) throws Exception {
            Continuation keepTrying = new Continuation(new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncSocketMiddleware.1.1
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    if (AnonymousClass1.this.lastException == null) {
                        AnonymousClass1.this.lastException = new ConnectionFailedException("Unable to connect to remote address");
                    }
                    if (AnonymousClass1.this.setComplete(AnonymousClass1.this.lastException)) {
                        AsyncSocketMiddleware.this.wrapCallback(AnonymousClass1.this.val$data, AnonymousClass1.this.val$uri, AnonymousClass1.this.val$port, false, AnonymousClass1.this.val$data.connectCallback).onConnectCompleted(AnonymousClass1.this.lastException, null);
                    }
                }
            });
            for (final InetAddress address : result) {
                final String inetSockAddress = String.format(Locale.ENGLISH, "%s:%s", address, Integer.valueOf(this.val$port));
                keepTrying.add(new ContinuationCallback() { // from class: com.koushikdutta.async.http.AsyncSocketMiddleware.1.2
                    @Override // com.koushikdutta.async.callback.ContinuationCallback
                    public void onContinue(Continuation continuation, final CompletedCallback next) throws Exception {
                        AnonymousClass1.this.val$data.request.logv("attempting connection to " + inetSockAddress);
                        AsyncSocketMiddleware.this.mClient.getServer().connectSocket(new InetSocketAddress(address, AnonymousClass1.this.val$port), AsyncSocketMiddleware.this.wrapCallback(AnonymousClass1.this.val$data, AnonymousClass1.this.val$uri, AnonymousClass1.this.val$port, false, new ConnectCallback() { // from class: com.koushikdutta.async.http.AsyncSocketMiddleware.1.2.1
                            @Override // com.koushikdutta.async.callback.ConnectCallback
                            public void onConnectCompleted(Exception ex, AsyncSocket socket) {
                                if (AnonymousClass1.this.isDone()) {
                                    AnonymousClass1.this.lastException = new Exception("internal error during connect to " + inetSockAddress);
                                    next.onCompleted(null);
                                } else if (ex != null) {
                                    AnonymousClass1.this.lastException = ex;
                                    next.onCompleted(null);
                                } else if (AnonymousClass1.this.isDone() || AnonymousClass1.this.isCancelled()) {
                                    AnonymousClass1.this.val$data.request.logd("Recycling extra socket leftover from cancelled operation");
                                    AsyncSocketMiddleware.this.idleSocket(socket);
                                    AsyncSocketMiddleware.this.recycleSocket(socket, AnonymousClass1.this.val$data.request);
                                } else if (AnonymousClass1.this.setComplete(null, socket)) {
                                    AnonymousClass1.this.val$data.connectCallback.onConnectCompleted(null, socket);
                                }
                            }
                        }));
                    }
                });
            }
            keepTrying.start();
        }
    }

    private ConnectionInfo getOrCreateConnectionInfo(String lookup) {
        ConnectionInfo info = this.connectionInfo.get(lookup);
        if (info == null) {
            ConnectionInfo info2 = new ConnectionInfo();
            this.connectionInfo.put(lookup, info2);
            return info2;
        }
        return info;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void maybeCleanupConnectionInfo(String lookup) {
        ConnectionInfo info = this.connectionInfo.get(lookup);
        if (info != null) {
            while (!info.sockets.isEmpty()) {
                IdleSocketHolder idleSocketHolder = info.sockets.peekLast();
                AsyncSocket socket = idleSocketHolder.socket;
                if (idleSocketHolder.idleTime + ((long) this.idleTimeoutMs) > System.currentTimeMillis()) {
                    break;
                }
                info.sockets.pop();
                socket.setClosedCallback(null);
                socket.close();
            }
            if (info.openCount == 0 && info.queue.isEmpty() && info.sockets.isEmpty()) {
                this.connectionInfo.remove(lookup);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void recycleSocket(AsyncSocket socket, AsyncHttpRequest request) {
        final ArrayDeque<IdleSocketHolder> sockets;
        if (socket != null) {
            Uri uri = request.getUri();
            int port = getSchemePort(uri);
            final String lookup = computeLookup(uri, port, request.getProxyHost(), request.getProxyPort());
            final IdleSocketHolder idleSocketHolder = new IdleSocketHolder(socket);
            synchronized (this) {
                ConnectionInfo info = getOrCreateConnectionInfo(lookup);
                sockets = info.sockets;
                sockets.push(idleSocketHolder);
            }
            socket.setClosedCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncSocketMiddleware.2
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    synchronized (AsyncSocketMiddleware.this) {
                        sockets.remove(idleSocketHolder);
                        AsyncSocketMiddleware.this.maybeCleanupConnectionInfo(lookup);
                    }
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void idleSocket(final AsyncSocket socket) {
        socket.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.AsyncSocketMiddleware.3
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                socket.setClosedCallback(null);
                socket.close();
            }
        });
        socket.setWriteableCallback(null);
        socket.setDataCallback(new DataCallback.NullDataCallback() { // from class: com.koushikdutta.async.http.AsyncSocketMiddleware.4
            @Override // com.koushikdutta.async.callback.DataCallback.NullDataCallback, com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                super.onDataAvailable(emitter, bb);
                bb.recycle();
                socket.setClosedCallback(null);
                socket.close();
            }
        });
    }

    private void nextConnection(AsyncHttpRequest request) {
        Uri uri = request.getUri();
        int port = getSchemePort(uri);
        String key = computeLookup(uri, port, request.getProxyHost(), request.getProxyPort());
        synchronized (this) {
            ConnectionInfo info = this.connectionInfo.get(key);
            if (info != null) {
                info.openCount--;
                while (info.openCount < this.maxConnectionCount && info.queue.size() > 0) {
                    AsyncHttpClientMiddleware.GetSocketData gsd = info.queue.remove();
                    SimpleCancellable socketCancellable = (SimpleCancellable) gsd.socketCancellable;
                    if (!socketCancellable.isCancelled()) {
                        Cancellable connect = getSocket(gsd);
                        socketCancellable.setParent(connect);
                    }
                }
                maybeCleanupConnectionInfo(key);
            }
        }
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onResponseComplete(AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data) {
        if (data.state.get("socket-owner") == this) {
            try {
                idleSocket(data.socket);
                if (data.exception != null || !data.socket.isOpen()) {
                    data.request.logv("closing out socket (exception)");
                    data.socket.setClosedCallback(null);
                    data.socket.close();
                } else if (!HttpUtil.isKeepAlive(data.response.protocol(), data.response.headers()) || !HttpUtil.isKeepAlive(Protocol.HTTP_1_1, data.request.getHeaders())) {
                    data.request.logv("closing out socket (not keep alive)");
                    data.socket.setClosedCallback(null);
                    data.socket.close();
                    nextConnection(data.request);
                } else {
                    data.request.logd("Recycling keep-alive socket");
                    recycleSocket(data.socket, data.request);
                    nextConnection(data.request);
                }
            } finally {
                nextConnection(data.request);
            }
        }
    }
}
