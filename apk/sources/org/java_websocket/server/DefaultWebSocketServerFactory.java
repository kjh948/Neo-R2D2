package org.java_websocket.server;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.server.WebSocketServer;

/* JADX INFO: loaded from: classes.dex */
public class DefaultWebSocketServerFactory implements WebSocketServer.WebSocketServerFactory {
    @Override // org.java_websocket.WebSocketFactory
    public /* bridge */ /* synthetic */ WebSocket createWebSocket(WebSocketAdapter webSocketAdapter, List list) {
        return createWebSocket(webSocketAdapter, (List<Draft>) list);
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
    public SocketChannel wrapChannel(SocketChannel channel, SelectionKey key) {
        return channel;
    }

    @Override // org.java_websocket.server.WebSocketServer.WebSocketServerFactory
    public void close() {
    }
}
