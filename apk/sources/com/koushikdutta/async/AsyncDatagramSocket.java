package com.koushikdutta.async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class AsyncDatagramSocket extends AsyncNetworkSocket {
    public void disconnect() throws IOException {
        this.socketAddress = null;
        ((DatagramChannelWrapper) getChannel()).disconnect();
    }

    @Override // com.koushikdutta.async.AsyncNetworkSocket
    public InetSocketAddress getRemoteAddress() {
        return isOpen() ? super.getRemoteAddress() : ((DatagramChannelWrapper) getChannel()).getRemoteAddress();
    }

    public void connect(InetSocketAddress address) throws IOException {
        this.socketAddress = address;
        ((DatagramChannelWrapper) getChannel()).mChannel.connect(address);
    }

    public void send(final String host, final int port, final ByteBuffer buffer) {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().run(new Runnable() { // from class: com.koushikdutta.async.AsyncDatagramSocket.1
                @Override // java.lang.Runnable
                public void run() {
                    AsyncDatagramSocket.this.send(host, port, buffer);
                }
            });
        } else {
            try {
                ((DatagramChannelWrapper) getChannel()).mChannel.send(buffer, new InetSocketAddress(host, port));
            } catch (IOException e) {
            }
        }
    }

    public void send(final InetSocketAddress address, final ByteBuffer buffer) {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().run(new Runnable() { // from class: com.koushikdutta.async.AsyncDatagramSocket.2
                @Override // java.lang.Runnable
                public void run() {
                    AsyncDatagramSocket.this.send(address, buffer);
                }
            });
        } else {
            try {
                ((DatagramChannelWrapper) getChannel()).mChannel.send(buffer, new InetSocketAddress(address.getHostName(), address.getPort()));
            } catch (IOException e) {
            }
        }
    }
}
