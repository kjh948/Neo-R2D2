package org.java_websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import org.java_websocket.WebSocket;

/* JADX INFO: loaded from: classes.dex */
public class SocketChannelIOHelper {
    public static boolean read(ByteBuffer buf, WebSocketImpl ws, ByteChannel channel) throws IOException {
        buf.clear();
        int read = channel.read(buf);
        buf.flip();
        if (read != -1) {
            return read != 0;
        }
        ws.eot();
        return false;
    }

    public static boolean readMore(ByteBuffer buf, WebSocketImpl ws, WrappedByteChannel channel) throws IOException {
        buf.clear();
        int read = channel.readMore(buf);
        buf.flip();
        if (read != -1) {
            return channel.isNeedRead();
        }
        ws.eot();
        return false;
    }

    public static boolean batch(WebSocketImpl ws, ByteChannel sockchannel) throws IOException {
        ByteBuffer buffer = ws.outQueue.peek();
        WrappedByteChannel c = null;
        if (buffer == null) {
            if (sockchannel instanceof WrappedByteChannel) {
                c = (WrappedByteChannel) sockchannel;
                if (c.isNeedWrite()) {
                    c.writeMore();
                }
            }
        } else {
            do {
                sockchannel.write(buffer);
                if (buffer.remaining() > 0) {
                    return false;
                }
                ws.outQueue.poll();
                buffer = ws.outQueue.peek();
            } while (buffer != null);
        }
        if (ws != null && ws.outQueue.isEmpty() && ws.isFlushAndClose() && ws.getDraft() != null && ws.getDraft().getRole() != null && ws.getDraft().getRole() == WebSocket.Role.SERVER) {
            synchronized (ws) {
                ws.closeConnection();
            }
        }
        return c == null || !((WrappedByteChannel) sockchannel).isNeedWrite();
    }
}
