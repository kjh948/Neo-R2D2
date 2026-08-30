package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;

/* JADX INFO: loaded from: classes.dex */
abstract class ChannelWrapper implements ReadableByteChannel, ScatteringByteChannel {
    private AbstractSelectableChannel mChannel;

    public abstract int getLocalPort();

    public abstract Object getSocket();

    public abstract boolean isConnected();

    public abstract SelectionKey register(Selector selector) throws ClosedChannelException;

    public abstract void shutdownInput();

    public abstract void shutdownOutput();

    public abstract int write(ByteBuffer byteBuffer) throws IOException;

    public abstract int write(ByteBuffer[] byteBufferArr) throws IOException;

    ChannelWrapper(AbstractSelectableChannel channel) throws IOException {
        channel.configureBlocking(false);
        this.mChannel = channel;
    }

    public SelectionKey register(Selector sel, int ops) throws ClosedChannelException {
        return this.mChannel.register(sel, ops);
    }

    public boolean isChunked() {
        return false;
    }

    @Override // java.nio.channels.Channel
    public boolean isOpen() {
        return this.mChannel.isOpen();
    }

    @Override // java.nio.channels.Channel, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        this.mChannel.close();
    }
}
