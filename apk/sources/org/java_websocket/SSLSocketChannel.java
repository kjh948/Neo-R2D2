package org.java_websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import org.java_websocket.util.ByteBufferUtils;

/* JADX INFO: loaded from: classes.dex */
public class SSLSocketChannel implements WrappedByteChannel, ByteChannel {
    private final SSLEngine engine;
    private ExecutorService executor;
    private ByteBuffer myAppData;
    private ByteBuffer myNetData;
    private ByteBuffer peerAppData;
    private ByteBuffer peerNetData;
    private SelectionKey selectionKey;
    private final SocketChannel socketChannel;

    public SSLSocketChannel(SocketChannel inputSocketChannel, SSLEngine inputEngine, ExecutorService inputExecutor, SelectionKey key) throws IOException {
        if (inputSocketChannel == null || inputEngine == null || this.executor == inputExecutor) {
            throw new IllegalArgumentException("parameter must not be null");
        }
        this.socketChannel = inputSocketChannel;
        this.engine = inputEngine;
        this.executor = inputExecutor;
        this.myNetData = ByteBuffer.allocate(this.engine.getSession().getPacketBufferSize());
        this.peerNetData = ByteBuffer.allocate(this.engine.getSession().getPacketBufferSize());
        this.engine.beginHandshake();
        if (doHandshake()) {
            if (key != null) {
                key.interestOps(key.interestOps() | 4);
                this.selectionKey = key;
                return;
            }
            return;
        }
        try {
            this.socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override // java.nio.channels.ReadableByteChannel
    public synchronized int read(ByteBuffer dst) throws IOException {
        int bytesRead;
        if (!dst.hasRemaining()) {
            bytesRead = 0;
        } else if (this.peerAppData.hasRemaining()) {
            this.peerAppData.flip();
            bytesRead = ByteBufferUtils.transferByteBuffer(this.peerAppData, dst);
        } else {
            this.peerNetData.compact();
            bytesRead = this.socketChannel.read(this.peerNetData);
            if (bytesRead > 0 || this.peerNetData.hasRemaining()) {
                this.peerNetData.flip();
                while (this.peerNetData.hasRemaining()) {
                    this.peerAppData.compact();
                    try {
                        SSLEngineResult result = this.engine.unwrap(this.peerNetData, this.peerAppData);
                        switch (AnonymousClass1.$SwitchMap$javax$net$ssl$SSLEngineResult$Status[result.getStatus().ordinal()]) {
                            case 1:
                                this.peerAppData.flip();
                                bytesRead = ByteBufferUtils.transferByteBuffer(this.peerAppData, dst);
                                break;
                            case 2:
                                this.peerAppData.flip();
                                bytesRead = ByteBufferUtils.transferByteBuffer(this.peerAppData, dst);
                                break;
                            case 3:
                                this.peerAppData = enlargeApplicationBuffer(this.peerAppData);
                                break;
                            case 4:
                                closeConnection();
                                dst.clear();
                                bytesRead = -1;
                                break;
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                    } catch (SSLException e) {
                        e.printStackTrace();
                        throw e;
                    }
                }
            } else if (bytesRead < 0) {
                handleEndOfStream();
            }
            ByteBufferUtils.transferByteBuffer(this.peerAppData, dst);
        }
        return bytesRead;
    }

    @Override // java.nio.channels.WritableByteChannel
    public synchronized int write(ByteBuffer output) throws IOException {
        int num;
        num = 0;
        while (true) {
            if (output.hasRemaining()) {
                this.myNetData.clear();
                SSLEngineResult result = this.engine.wrap(output, this.myNetData);
                switch (AnonymousClass1.$SwitchMap$javax$net$ssl$SSLEngineResult$Status[result.getStatus().ordinal()]) {
                    case 1:
                        this.myNetData.flip();
                        while (this.myNetData.hasRemaining()) {
                            num += this.socketChannel.write(this.myNetData);
                        }
                        continue;
                    case 2:
                        throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                    case 3:
                        this.myNetData = enlargePacketBuffer(this.myNetData);
                        continue;
                    case 4:
                        closeConnection();
                        num = 0;
                        break;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }
        }
        return num;
    }

    private boolean doHandshake() throws IOException {
        int appBufferSize = this.engine.getSession().getApplicationBufferSize();
        this.myAppData = ByteBuffer.allocate(appBufferSize);
        this.peerAppData = ByteBuffer.allocate(appBufferSize);
        this.myNetData.clear();
        this.peerNetData.clear();
        SSLEngineResult.HandshakeStatus handshakeStatus = this.engine.getHandshakeStatus();
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (AnonymousClass1.$SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus[handshakeStatus.ordinal()]) {
                case 1:
                    if (this.socketChannel.read(this.peerNetData) < 0) {
                        if (this.engine.isInboundDone() && this.engine.isOutboundDone()) {
                            return false;
                        }
                        try {
                            this.engine.closeInbound();
                            break;
                        } catch (SSLException e) {
                        }
                        this.engine.closeOutbound();
                        handshakeStatus = this.engine.getHandshakeStatus();
                        break;
                    } else {
                        this.peerNetData.flip();
                        try {
                            SSLEngineResult result = this.engine.unwrap(this.peerNetData, this.peerAppData);
                            this.peerNetData.compact();
                            handshakeStatus = result.getHandshakeStatus();
                            switch (AnonymousClass1.$SwitchMap$javax$net$ssl$SSLEngineResult$Status[result.getStatus().ordinal()]) {
                                case 1:
                                    break;
                                case 2:
                                    this.peerNetData = handleBufferUnderflow(this.peerNetData);
                                    break;
                                case 3:
                                    this.peerAppData = enlargeApplicationBuffer(this.peerAppData);
                                    break;
                                case 4:
                                    if (this.engine.isOutboundDone()) {
                                        return false;
                                    }
                                    this.engine.closeOutbound();
                                    handshakeStatus = this.engine.getHandshakeStatus();
                                    break;
                                default:
                                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                            }
                        } catch (SSLException e2) {
                            this.engine.closeOutbound();
                            handshakeStatus = this.engine.getHandshakeStatus();
                            break;
                        }
                    }
                    break;
                case 2:
                    this.myNetData.clear();
                    try {
                        SSLEngineResult result2 = this.engine.wrap(this.myAppData, this.myNetData);
                        handshakeStatus = result2.getHandshakeStatus();
                        switch (AnonymousClass1.$SwitchMap$javax$net$ssl$SSLEngineResult$Status[result2.getStatus().ordinal()]) {
                            case 1:
                                this.myNetData.flip();
                                while (this.myNetData.hasRemaining()) {
                                    this.socketChannel.write(this.myNetData);
                                }
                                break;
                            case 2:
                                throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                            case 3:
                                this.myNetData = enlargePacketBuffer(this.myNetData);
                                break;
                            case 4:
                                try {
                                    this.myNetData.flip();
                                    while (this.myNetData.hasRemaining()) {
                                        this.socketChannel.write(this.myNetData);
                                        break;
                                    }
                                    this.peerNetData.clear();
                                } catch (Exception e3) {
                                    handshakeStatus = this.engine.getHandshakeStatus();
                                }
                                break;
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + result2.getStatus());
                        }
                    } catch (SSLException e4) {
                        this.engine.closeOutbound();
                        handshakeStatus = this.engine.getHandshakeStatus();
                        break;
                    }
                    break;
                case 3:
                    while (true) {
                        Runnable task = this.engine.getDelegatedTask();
                        if (task != null) {
                            this.executor.execute(task);
                        } else {
                            handshakeStatus = this.engine.getHandshakeStatus();
                        }
                        break;
                    }
                    break;
                case 4:
                case 5:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + handshakeStatus);
            }
        }
        return true;
    }

    /* JADX INFO: renamed from: org.java_websocket.SSLSocketChannel$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus = new int[SSLEngineResult.HandshakeStatus.values().length];
        static final /* synthetic */ int[] $SwitchMap$javax$net$ssl$SSLEngineResult$Status;

        static {
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus[SSLEngineResult.HandshakeStatus.NEED_UNWRAP.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus[SSLEngineResult.HandshakeStatus.NEED_WRAP.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus[SSLEngineResult.HandshakeStatus.NEED_TASK.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus[SSLEngineResult.HandshakeStatus.FINISHED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$HandshakeStatus[SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            $SwitchMap$javax$net$ssl$SSLEngineResult$Status = new int[SSLEngineResult.Status.values().length];
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$Status[SSLEngineResult.Status.OK.ordinal()] = 1;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$Status[SSLEngineResult.Status.BUFFER_UNDERFLOW.ordinal()] = 2;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$Status[SSLEngineResult.Status.BUFFER_OVERFLOW.ordinal()] = 3;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$javax$net$ssl$SSLEngineResult$Status[SSLEngineResult.Status.CLOSED.ordinal()] = 4;
            } catch (NoSuchFieldError e9) {
            }
        }
    }

    private ByteBuffer enlargePacketBuffer(ByteBuffer buffer) {
        return enlargeBuffer(buffer, this.engine.getSession().getPacketBufferSize());
    }

    private ByteBuffer enlargeApplicationBuffer(ByteBuffer buffer) {
        return enlargeBuffer(buffer, this.engine.getSession().getApplicationBufferSize());
    }

    private ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity > buffer.capacity()) {
            return ByteBuffer.allocate(sessionProposedCapacity);
        }
        return ByteBuffer.allocate(buffer.capacity() * 2);
    }

    private ByteBuffer handleBufferUnderflow(ByteBuffer buffer) {
        if (this.engine.getSession().getPacketBufferSize() >= buffer.limit()) {
            ByteBuffer replaceBuffer = enlargePacketBuffer(buffer);
            buffer.flip();
            replaceBuffer.put(buffer);
            return replaceBuffer;
        }
        return buffer;
    }

    private void closeConnection() throws IOException {
        this.engine.closeOutbound();
        try {
            doHandshake();
        } catch (IOException e) {
        }
        this.socketChannel.close();
    }

    private void handleEndOfStream() throws IOException {
        try {
            this.engine.closeInbound();
        } catch (Exception e) {
            System.err.println("This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
        }
        closeConnection();
    }

    @Override // org.java_websocket.WrappedByteChannel
    public boolean isNeedWrite() {
        return false;
    }

    @Override // org.java_websocket.WrappedByteChannel
    public void writeMore() throws IOException {
    }

    @Override // org.java_websocket.WrappedByteChannel
    public boolean isNeedRead() {
        return this.peerNetData.hasRemaining() || this.peerAppData.hasRemaining();
    }

    @Override // org.java_websocket.WrappedByteChannel
    public int readMore(ByteBuffer dst) throws IOException {
        return read(dst);
    }

    @Override // org.java_websocket.WrappedByteChannel
    public boolean isBlocking() {
        return this.socketChannel.isBlocking();
    }

    @Override // java.nio.channels.Channel
    public boolean isOpen() {
        return this.socketChannel.isOpen();
    }

    @Override // java.nio.channels.Channel, java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        closeConnection();
    }
}
