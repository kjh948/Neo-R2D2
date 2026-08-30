package com.koushikdutta.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/* JADX INFO: loaded from: classes.dex */
class SocketChannelWrapper extends ChannelWrapper {
    SocketChannel mChannel;

    @Override // com.koushikdutta.async.ChannelWrapper
    public int getLocalPort() {
        return this.mChannel.socket().getLocalPort();
    }

    SocketChannelWrapper(SocketChannel channel) throws IOException {
        super(channel);
        this.mChannel = channel;
    }

    @Override // java.nio.channels.ReadableByteChannel
    public int read(ByteBuffer buffer) throws IOException {
        return this.mChannel.read(buffer);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public boolean isConnected() {
        return this.mChannel.isConnected();
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public int write(ByteBuffer src) throws IOException {
        return this.mChannel.write(src);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public int write(ByteBuffer[] src) throws IOException {
        return (int) this.mChannel.write(src);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public SelectionKey register(Selector sel) throws ClosedChannelException {
        return register(sel, 8);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public void shutdownOutput() {
        try {
            this.mChannel.socket().shutdownOutput();
        } catch (Exception e) {
        }
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public void shutdownInput() {
        try {
            this.mChannel.socket().shutdownInput();
        } catch (Exception e) {
        }
    }

    @Override // java.nio.channels.ScatteringByteChannel
    public long read(ByteBuffer[] byteBuffers) throws IOException {
        return this.mChannel.read(byteBuffers);
    }

    @Override // java.nio.channels.ScatteringByteChannel
    public long read(ByteBuffer[] byteBuffers, int i, int i2) throws IOException {
        return this.mChannel.read(byteBuffers, i, i2);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public Object getSocket() {
        return this.mChannel.socket();
    }
}
