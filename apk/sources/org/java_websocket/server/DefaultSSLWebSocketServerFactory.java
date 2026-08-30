package org.java_websocket.server;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.java_websocket.SSLSocketChannel2;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.server.WebSocketServer;

/* JADX INFO: loaded from: classes.dex */
public class DefaultSSLWebSocketServerFactory implements WebSocketServer.WebSocketServerFactory {
    protected ExecutorService exec;
    protected SSLContext sslcontext;

    @Override // org.java_websocket.WebSocketFactory
    public /* bridge */ /* synthetic */ WebSocket createWebSocket(WebSocketAdapter webSocketAdapter, List list) {
        return createWebSocket(webSocketAdapter, (List<Draft>) list);
    }

    public DefaultSSLWebSocketServerFactory(SSLContext sslContext) {
        this(sslContext, Executors.newSingleThreadScheduledExecutor());
    }

    public DefaultSSLWebSocketServerFactory(SSLContext sslContext, ExecutorService exec) {
        if (sslContext == null || exec == null) {
            throw new IllegalArgumentException();
        }
        this.sslcontext = sslContext;
        this.exec = exec;
    }

    @Override // org.java_websocket.server.WebSocketServer.WebSocketServerFactory
    public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
        SSLEngine e = this.sslcontext.createSSLEngine();
        List<String> ciphers = new ArrayList<>(Arrays.asList(e.getEnabledCipherSuites()));
        ciphers.remove("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        e.setEnabledCipherSuites((String[]) ciphers.toArray(new String[0]));
        e.setUseClientMode(false);
        return new SSLSocketChannel2(channel, e, this.exec, key);
    }

    @Override // org.java_websocket.WebSocketFactory
    public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d) {
        return new WebSocketImpl(a, d);
    }

    @Override // org.java_websocket.server.WebSocketServer.WebSocketServerFactory, org.java_websocket.WebSocketFactory
    public WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> d) {
        return new WebSocketImpl(a, d);
    }

    @Override // org.java_websocket.server.WebSocketServer.WebSocketServerFactory
    public void close() {
        this.exec.shutdown();
    }
}
