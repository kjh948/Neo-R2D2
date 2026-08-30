package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.spdy.FrameReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class AsyncSpdyConnection implements FrameReader.Handler {
    private static final int OKHTTP_CLIENT_WINDOW_SIZE = 16777216;
    BufferedDataSink bufferedSocket;
    long bytesLeftInWriteWindow;
    private int lastGoodStreamId;
    private int nextPingId;
    private int nextStreamId;
    private Map<Integer, Ping> pings;
    Protocol protocol;
    FrameReader reader;
    boolean shutdown;
    AsyncSocket socket;
    int totalWindowRead;
    Variant variant;
    FrameWriter writer;
    Hashtable<Integer, SpdySocket> sockets = new Hashtable<>();
    boolean client = true;
    final Settings okHttpSettings = new Settings();
    Settings peerSettings = new Settings();
    private boolean receivedInitialPeerSettings = false;

    public SpdySocket newStream(List<Header> requestHeaders, boolean out, boolean in) {
        return newStream(0, requestHeaders, out, in);
    }

    private SpdySocket newStream(int associatedStreamId, List<Header> requestHeaders, boolean out, boolean in) {
        boolean outFinished = !out;
        boolean inFinished = !in;
        if (this.shutdown) {
            return null;
        }
        int streamId = this.nextStreamId;
        this.nextStreamId += 2;
        SpdySocket socket = new SpdySocket(streamId, outFinished, inFinished, requestHeaders);
        if (socket.isOpen()) {
            this.sockets.put(Integer.valueOf(streamId), socket);
        }
        try {
            if (associatedStreamId == 0) {
                this.writer.synStream(outFinished, inFinished, streamId, associatedStreamId, requestHeaders);
                return socket;
            }
            if (this.client) {
                throw new IllegalArgumentException("client streams shouldn't have associated stream IDs");
            }
            this.writer.pushPromise(associatedStreamId, streamId, requestHeaders);
            return socket;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    void updateWindowRead(int length) {
        this.totalWindowRead += length;
        if (this.totalWindowRead >= this.okHttpSettings.getInitialWindowSize(65536) / 2) {
            try {
                this.writer.windowUpdate(0, this.totalWindowRead);
                this.totalWindowRead = 0;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }

    public class SpdySocket implements AsyncSocket {
        long bytesLeftInWriteWindow;
        CompletedCallback closedCallback;
        DataCallback dataCallback;
        CompletedCallback endCallback;
        final int id;
        boolean paused;
        int totalWindowRead;
        WritableCallback writable;
        ByteBufferList pending = new ByteBufferList();
        SimpleFuture<List<Header>> headers = new SimpleFuture<>();
        boolean isOpen = true;
        ByteBufferList writing = new ByteBufferList();

        public AsyncSpdyConnection getConnection() {
            return AsyncSpdyConnection.this;
        }

        public SimpleFuture<List<Header>> headers() {
            return this.headers;
        }

        void updateWindowRead(int length) {
            this.totalWindowRead += length;
            if (this.totalWindowRead >= AsyncSpdyConnection.this.okHttpSettings.getInitialWindowSize(65536) / 2) {
                try {
                    AsyncSpdyConnection.this.writer.windowUpdate(this.id, this.totalWindowRead);
                    this.totalWindowRead = 0;
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
            AsyncSpdyConnection.this.updateWindowRead(length);
        }

        public SpdySocket(int id, boolean outFinished, boolean inFinished, List<Header> headerBlock) {
            this.bytesLeftInWriteWindow = AsyncSpdyConnection.this.peerSettings.getInitialWindowSize(65536);
            this.id = id;
        }

        public boolean isLocallyInitiated() {
            boolean streamIsClient = (this.id & 1) == 1;
            return AsyncSpdyConnection.this.client == streamIsClient;
        }

        public void addBytesToWriteWindow(long delta) {
            long prev = this.bytesLeftInWriteWindow;
            this.bytesLeftInWriteWindow += delta;
            if (this.bytesLeftInWriteWindow > 0 && prev <= 0) {
                com.koushikdutta.async.Util.writable(this.writable);
            }
        }

        @Override // com.koushikdutta.async.AsyncSocket, com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
        public AsyncServer getServer() {
            return AsyncSpdyConnection.this.socket.getServer();
        }

        @Override // com.koushikdutta.async.DataEmitter
        public void setDataCallback(DataCallback callback) {
            this.dataCallback = callback;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public DataCallback getDataCallback() {
            return this.dataCallback;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public boolean isChunked() {
            return false;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public void pause() {
            this.paused = true;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public void resume() {
            this.paused = false;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public void close() {
            this.isOpen = false;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public boolean isPaused() {
            return this.paused;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public void setEndCallback(CompletedCallback callback) {
            this.endCallback = callback;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public CompletedCallback getEndCallback() {
            return this.endCallback;
        }

        @Override // com.koushikdutta.async.DataEmitter
        public String charset() {
            return null;
        }

        @Override // com.koushikdutta.async.DataSink
        public void write(ByteBufferList bb) {
            int canWrite = Math.min(bb.remaining(), (int) Math.min(this.bytesLeftInWriteWindow, AsyncSpdyConnection.this.bytesLeftInWriteWindow));
            if (canWrite != 0) {
                if (canWrite < bb.remaining()) {
                    if (this.writing.hasRemaining()) {
                        throw new AssertionError("wtf");
                    }
                    bb.get(this.writing, canWrite);
                    bb = this.writing;
                }
                try {
                    AsyncSpdyConnection.this.writer.data(false, this.id, bb);
                    this.bytesLeftInWriteWindow -= (long) canWrite;
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        }

        @Override // com.koushikdutta.async.DataSink
        public void setWriteableCallback(WritableCallback handler) {
            this.writable = handler;
        }

        @Override // com.koushikdutta.async.DataSink
        public WritableCallback getWriteableCallback() {
            return this.writable;
        }

        @Override // com.koushikdutta.async.DataSink
        public boolean isOpen() {
            return this.isOpen;
        }

        @Override // com.koushikdutta.async.DataSink
        public void end() {
            try {
                AsyncSpdyConnection.this.writer.data(true, this.id, this.writing);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        @Override // com.koushikdutta.async.DataSink
        public void setClosedCallback(CompletedCallback handler) {
            this.closedCallback = handler;
        }

        @Override // com.koushikdutta.async.DataSink
        public CompletedCallback getClosedCallback() {
            return this.closedCallback;
        }

        public void receiveHeaders(List<Header> headers, HeadersMode headerMode) {
            this.headers.setComplete(headers);
        }
    }

    public AsyncSpdyConnection(AsyncSocket socket, Protocol protocol) {
        this.protocol = protocol;
        this.socket = socket;
        this.bufferedSocket = new BufferedDataSink(socket);
        if (protocol == Protocol.SPDY_3) {
            this.variant = new Spdy3();
        } else if (protocol == Protocol.HTTP_2) {
            this.variant = new Http20Draft13();
        }
        this.reader = this.variant.newReader(socket, this, true);
        this.writer = this.variant.newWriter(this.bufferedSocket, true);
        this.nextStreamId = 1 != 0 ? 1 : 2;
        if (1 != 0 && protocol == Protocol.HTTP_2) {
            this.nextStreamId += 2;
        }
        this.nextPingId = 1 == 0 ? 2 : 1;
        if (1 != 0) {
            this.okHttpSettings.set(7, 0, 16777216);
        }
    }

    public void sendConnectionPreface() throws IOException {
        this.writer.connectionPreface();
        this.writer.settings(this.okHttpSettings);
        int windowSize = this.okHttpSettings.getInitialWindowSize(65536);
        if (windowSize != 65536) {
            this.writer.windowUpdate(0, windowSize - 65536);
        }
    }

    private boolean pushedStream(int streamId) {
        return this.protocol == Protocol.HTTP_2 && streamId != 0 && (streamId & 1) == 0;
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void data(boolean inFinished, int streamId, ByteBufferList source) {
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
        }
        SpdySocket socket = this.sockets.get(Integer.valueOf(streamId));
        if (socket == null) {
            try {
                this.writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
                source.recycle();
                return;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        int length = source.remaining();
        source.get(socket.pending);
        socket.updateWindowRead(length);
        com.koushikdutta.async.Util.emitAllData(socket, socket.pending);
        if (inFinished) {
            this.sockets.remove(Integer.valueOf(streamId));
            socket.close();
            com.koushikdutta.async.Util.end(socket, (Exception) null);
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void headers(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, List<Header> headerBlock, HeadersMode headersMode) {
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
        }
        if (!this.shutdown) {
            SpdySocket socket = this.sockets.get(Integer.valueOf(streamId));
            if (socket == null) {
                if (headersMode.failIfStreamAbsent()) {
                    try {
                        this.writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
                        return;
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                } else {
                    if (streamId > this.lastGoodStreamId && streamId % 2 != this.nextStreamId % 2) {
                        throw new AssertionError("unexpected receive stream");
                    }
                    return;
                }
            }
            if (headersMode.failIfStreamPresent()) {
                try {
                    this.writer.rstStream(streamId, ErrorCode.INVALID_STREAM);
                    this.sockets.remove(Integer.valueOf(streamId));
                    return;
                } catch (IOException e2) {
                    throw new AssertionError(e2);
                }
            }
            socket.receiveHeaders(headerBlock, headersMode);
            if (inFinished) {
                this.sockets.remove(Integer.valueOf(streamId));
                com.koushikdutta.async.Util.end(socket, (Exception) null);
            }
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void rstStream(int streamId, ErrorCode errorCode) {
        if (pushedStream(streamId)) {
            throw new AssertionError("push");
        }
        SpdySocket rstStream = this.sockets.remove(Integer.valueOf(streamId));
        if (rstStream != null) {
            com.koushikdutta.async.Util.end(rstStream, new IOException(errorCode.toString()));
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void settings(boolean clearPrevious, Settings settings) {
        long delta = 0;
        int priorWriteWindowSize = this.peerSettings.getInitialWindowSize(65536);
        if (clearPrevious) {
            this.peerSettings.clear();
        }
        this.peerSettings.merge(settings);
        try {
            this.writer.ackSettings();
            int peerInitialWindowSize = this.peerSettings.getInitialWindowSize(65536);
            if (peerInitialWindowSize != -1 && peerInitialWindowSize != priorWriteWindowSize) {
                delta = peerInitialWindowSize - priorWriteWindowSize;
                if (!this.receivedInitialPeerSettings) {
                    addBytesToWriteWindow(delta);
                    this.receivedInitialPeerSettings = true;
                }
            }
            for (SpdySocket socket : this.sockets.values()) {
                socket.addBytesToWriteWindow(delta);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    void addBytesToWriteWindow(long delta) {
        this.bytesLeftInWriteWindow += delta;
        for (SpdySocket socket : this.sockets.values()) {
            com.koushikdutta.async.Util.writable(socket);
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void ackSettings() {
        try {
            this.writer.ackSettings();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void writePing(boolean reply, int payload1, int payload2, Ping ping) throws IOException {
        if (ping != null) {
            ping.send();
        }
        this.writer.ping(reply, payload1, payload2);
    }

    private synchronized Ping removePing(int id) {
        return this.pings != null ? this.pings.remove(Integer.valueOf(id)) : null;
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void ping(boolean ack, int payload1, int payload2) {
        if (ack) {
            Ping ping = removePing(payload1);
            if (ping != null) {
                ping.receive();
                return;
            }
            return;
        }
        try {
            writePing(true, payload1, payload2, null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void goAway(int lastGoodStreamId, ErrorCode errorCode, ByteString debugData) {
        this.shutdown = true;
        Iterator<Map.Entry<Integer, SpdySocket>> i = this.sockets.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<Integer, SpdySocket> entry = i.next();
            int streamId = entry.getKey().intValue();
            if (streamId > lastGoodStreamId && entry.getValue().isLocallyInitiated()) {
                com.koushikdutta.async.Util.end(entry.getValue(), new IOException(ErrorCode.REFUSED_STREAM.toString()));
                i.remove();
            }
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void windowUpdate(int streamId, long windowSizeIncrement) {
        if (streamId == 0) {
            addBytesToWriteWindow(windowSizeIncrement);
            return;
        }
        SpdySocket socket = this.sockets.get(Integer.valueOf(streamId));
        if (socket != null) {
            socket.addBytesToWriteWindow(windowSizeIncrement);
        }
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void priority(int streamId, int streamDependency, int weight, boolean exclusive) {
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders) {
        throw new AssertionError("pushPromise");
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void alternateService(int streamId, String origin, ByteString protocol, String host, int port, long maxAge) {
    }

    @Override // com.koushikdutta.async.http.spdy.FrameReader.Handler
    public void error(Exception e) {
        this.socket.close();
        Iterator<Map.Entry<Integer, SpdySocket>> i = this.sockets.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<Integer, SpdySocket> entry = i.next();
            com.koushikdutta.async.Util.end(entry.getValue(), e);
            i.remove();
        }
    }
}
