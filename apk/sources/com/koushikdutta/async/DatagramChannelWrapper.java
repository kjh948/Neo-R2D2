package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/* JADX INFO: loaded from: classes.dex */
class DatagramChannelWrapper extends ChannelWrapper {
    InetSocketAddress address;
    DatagramChannel mChannel;

    @Override // com.koushikdutta.async.ChannelWrapper
    public int getLocalPort() {
        return this.mChannel.socket().getLocalPort();
    }

    public InetSocketAddress getRemoteAddress() {
        return this.address;
    }

    public void disconnect() throws IOException {
        this.mChannel.disconnect();
    }

    DatagramChannelWrapper(DatagramChannel channel) throws IOException {
        super(channel);
        this.mChannel = channel;
    }

    @Override // java.nio.channels.ReadableByteChannel
    public int read(ByteBuffer buffer) throws IOException {
        if (!isConnected()) {
            int position = buffer.position();
            this.address = (InetSocketAddress) this.mChannel.receive(buffer);
            if (this.address == null) {
                return -1;
            }
            return buffer.position() - position;
        }
        this.address = null;
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
    public SelectionKey register(Selector sel, int ops) throws ClosedChannelException {
        return this.mChannel.register(sel, ops);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public boolean isChunked() {
        return true;
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public SelectionKey register(Selector sel) throws ClosedChannelException {
        return register(sel, 1);
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public void shutdownOutput() {
    }

    @Override // com.koushikdutta.async.ChannelWrapper
    public void shutdownInput() {
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
