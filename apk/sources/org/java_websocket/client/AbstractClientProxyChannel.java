package org.java_websocket.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import org.java_websocket.AbstractWrappedByteChannel;

/* JADX INFO: loaded from: classes.dex */
public abstract class AbstractClientProxyChannel extends AbstractWrappedByteChannel {
    protected final ByteBuffer proxyHandshake;

    public abstract String buildHandShake();

    public AbstractClientProxyChannel(ByteChannel towrap) {
        super(towrap);
        try {
            this.proxyHandshake = ByteBuffer.wrap(buildHandShake().getBytes("ASCII"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override // org.java_websocket.AbstractWrappedByteChannel, java.nio.channels.WritableByteChannel
    public int write(ByteBuffer src) throws IOException {
        return !this.proxyHandshake.hasRemaining() ? super.write(src) : super.write(this.proxyHandshake);
    }
}
