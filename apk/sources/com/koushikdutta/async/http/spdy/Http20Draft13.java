package com.koushikdutta.async.http.spdy;

import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.ActivityChooserView;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataEmitterReader;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.spdy.FrameReader;
import com.koushikdutta.async.http.spdy.HpackDraft08;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.drafts.Draft_75;

/* JADX INFO: loaded from: classes.dex */
final class Http20Draft13 implements Variant {
    static final byte FLAG_ACK = 1;
    static final byte FLAG_COMPRESSED = 32;
    static final byte FLAG_END_HEADERS = 4;
    static final byte FLAG_END_PUSH_PROMISE = 4;
    static final byte FLAG_END_SEGMENT = 2;
    static final byte FLAG_END_STREAM = 1;
    static final byte FLAG_NONE = 0;
    static final byte FLAG_PADDED = 8;
    static final byte FLAG_PRIORITY = 32;
    static final int MAX_FRAME_SIZE = 16383;
    static final byte TYPE_CONTINUATION = 9;
    static final byte TYPE_DATA = 0;
    static final byte TYPE_GOAWAY = 7;
    static final byte TYPE_HEADERS = 1;
    static final byte TYPE_PING = 6;
    static final byte TYPE_PRIORITY = 2;
    static final byte TYPE_PUSH_PROMISE = 5;
    static final byte TYPE_RST_STREAM = 3;
    static final byte TYPE_SETTINGS = 4;
    static final byte TYPE_WINDOW_UPDATE = 8;
    private static final Logger logger = Logger.getLogger(Http20Draft13.class.getName());
    private static final ByteString CONNECTION_PREFACE = ByteString.encodeUtf8("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");

    Http20Draft13() {
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public Protocol getProtocol() {
        return Protocol.HTTP_2;
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public FrameReader newReader(DataEmitter source, FrameReader.Handler handler, boolean client) {
        return new Reader(source, handler, 4096, client);
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public FrameWriter newWriter(BufferedDataSink sink, boolean client) {
        return new Writer(sink, client);
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public int maxFrameSize() {
        return MAX_FRAME_SIZE;
    }

    static final class Reader implements FrameReader {
        private final boolean client;
        int continuingStreamId;
        private final DataEmitter emitter;
        byte flags;
        private final FrameReader.Handler handler;
        final HpackDraft08.Reader hpackReader;
        short length;
        byte pendingHeaderType;
        int promisedStreamId;
        int streamId;
        byte type;
        int w1;
        int w2;
        private final DataCallback onFrame = new DataCallback() { // from class: com.koushikdutta.async.http.spdy.Http20Draft13.Reader.1
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.order(ByteOrder.BIG_ENDIAN);
                Reader.this.w1 = bb.getInt();
                Reader.this.w2 = bb.getInt();
                Reader.this.length = (short) ((Reader.this.w1 & 1073676288) >> 16);
                Reader.this.type = (byte) ((Reader.this.w1 & MotionEventCompat.ACTION_POINTER_INDEX_MASK) >> 8);
                Reader.this.flags = (byte) (Reader.this.w1 & 255);
                Reader.this.streamId = Reader.this.w2 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
                if (Http20Draft13.logger.isLoggable(Level.FINE)) {
                    Http20Draft13.logger.fine(FrameLogger.formatHeader(true, Reader.this.streamId, Reader.this.length, Reader.this.type, Reader.this.flags));
                }
                Reader.this.reader.read(Reader.this.length, Reader.this.onFullFrame);
            }
        };
        private final DataCallback onFullFrame = new DataCallback() { // from class: com.koushikdutta.async.http.spdy.Http20Draft13.Reader.2
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                try {
                    switch (Reader.this.type) {
                        case 0:
                            Reader.this.readData(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 1:
                            Reader.this.readHeaders(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 2:
                            Reader.this.readPriority(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 3:
                            Reader.this.readRstStream(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 4:
                            Reader.this.readSettings(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 5:
                            Reader.this.readPushPromise(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 6:
                            Reader.this.readPing(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 7:
                            Reader.this.readGoAway(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 8:
                            Reader.this.readWindowUpdate(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        case 9:
                            Reader.this.readContinuation(bb, Reader.this.length, Reader.this.flags, Reader.this.streamId);
                            break;
                        default:
                            bb.recycle();
                            break;
                    }
                    Reader.this.parseFrameHeader();
                } catch (IOException e) {
                    Reader.this.handler.error(e);
                }
            }
        };
        private final DataEmitterReader reader = new DataEmitterReader();

        Reader(DataEmitter emitter, FrameReader.Handler handler, int headerTableSize, boolean client) {
            this.emitter = emitter;
            this.client = client;
            this.hpackReader = new HpackDraft08.Reader(headerTableSize);
            this.handler = handler;
            parseFrameHeader();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void parseFrameHeader() {
            this.emitter.setDataCallback(this.reader);
            this.reader.read(8, this.onFrame);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readHeaders(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (streamId == 0) {
                throw Http20Draft13.ioException("PROTOCOL_ERROR: TYPE_HEADERS streamId == 0", new Object[0]);
            }
            short padding = (flags & 8) != 0 ? (short) (source.get() & Draft_75.END_OF_FRAME) : (short) 0;
            if ((flags & 32) != 0) {
                readPriority(source, streamId);
                length = (short) (length - 5);
            }
            short length2 = Http20Draft13.lengthWithoutPadding(length, flags, padding);
            this.pendingHeaderType = this.type;
            readHeaderBlock(source, length2, padding, flags, streamId);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readContinuation(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (streamId != this.continuingStreamId) {
                throw new IOException("continuation stream id mismatch");
            }
            readHeaderBlock(source, length, (short) 0, flags, streamId);
        }

        private void readHeaderBlock(ByteBufferList source, short length, short padding, byte flags, int streamId) throws IOException {
            source.skip(padding);
            this.hpackReader.refill(source);
            this.hpackReader.readHeaders();
            this.hpackReader.emitReferenceSet();
            if ((flags & 4) != 0) {
                if (this.pendingHeaderType == 1) {
                    boolean endStream = (flags & 1) != 0;
                    this.handler.headers(false, endStream, streamId, -1, this.hpackReader.getAndReset(), HeadersMode.HTTP_20_HEADERS);
                    return;
                } else {
                    if (this.pendingHeaderType == 5) {
                        this.handler.pushPromise(streamId, this.promisedStreamId, this.hpackReader.getAndReset());
                        return;
                    }
                    throw new AssertionError("unknown header type");
                }
            }
            this.continuingStreamId = streamId;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readData(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            boolean inFinished = (flags & 1) != 0;
            boolean gzipped = (flags & 32) != 0;
            if (gzipped) {
                throw Http20Draft13.ioException("PROTOCOL_ERROR: FLAG_COMPRESSED without SETTINGS_COMPRESS_DATA", new Object[0]);
            }
            short padding = (flags & 8) != 0 ? (short) (source.get() & Draft_75.END_OF_FRAME) : (short) 0;
            Http20Draft13.lengthWithoutPadding(length, flags, padding);
            this.handler.data(inFinished, streamId, source);
            source.skip(padding);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readPriority(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (length != 5) {
                throw Http20Draft13.ioException("TYPE_PRIORITY length: %d != 5", Short.valueOf(length));
            }
            if (streamId == 0) {
                throw Http20Draft13.ioException("TYPE_PRIORITY streamId == 0", new Object[0]);
            }
            readPriority(source, streamId);
        }

        private void readPriority(ByteBufferList source, int streamId) throws IOException {
            int w1 = source.getInt();
            boolean exclusive = (Integer.MIN_VALUE & w1) != 0;
            int streamDependency = w1 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            int weight = (source.get() & Draft_75.END_OF_FRAME) + 1;
            this.handler.priority(streamId, streamDependency, weight, exclusive);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readRstStream(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (length != 4) {
                throw Http20Draft13.ioException("TYPE_RST_STREAM length: %d != 4", Short.valueOf(length));
            }
            if (streamId == 0) {
                throw Http20Draft13.ioException("TYPE_RST_STREAM streamId == 0", new Object[0]);
            }
            int errorCodeInt = source.getInt();
            ErrorCode errorCode = ErrorCode.fromHttp2(errorCodeInt);
            if (errorCode == null) {
                throw Http20Draft13.ioException("TYPE_RST_STREAM unexpected error code: %d", Integer.valueOf(errorCodeInt));
            }
            this.handler.rstStream(streamId, errorCode);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readSettings(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (streamId != 0) {
                throw Http20Draft13.ioException("TYPE_SETTINGS streamId != 0", new Object[0]);
            }
            if ((flags & 1) != 0) {
                if (length != 0) {
                    throw Http20Draft13.ioException("FRAME_SIZE_ERROR ack frame should be empty!", new Object[0]);
                }
                this.handler.ackSettings();
                return;
            }
            if (length % 6 != 0) {
                throw Http20Draft13.ioException("TYPE_SETTINGS length %% 6 != 0: %s", Short.valueOf(length));
            }
            Settings settings = new Settings();
            for (int i = 0; i < length; i += 6) {
                short id = source.getShort();
                int value = source.getInt();
                switch (id) {
                    case 1:
                    case 5:
                        break;
                    case 2:
                        if (value != 0 && value != 1) {
                            throw Http20Draft13.ioException("PROTOCOL_ERROR SETTINGS_ENABLE_PUSH != 0 or 1", new Object[0]);
                        }
                        break;
                        break;
                    case 3:
                        id = 4;
                        break;
                    case 4:
                        id = 7;
                        if (value < 0) {
                            throw Http20Draft13.ioException("PROTOCOL_ERROR SETTINGS_INITIAL_WINDOW_SIZE > 2^31 - 1", new Object[0]);
                        }
                        break;
                        break;
                    default:
                        throw Http20Draft13.ioException("PROTOCOL_ERROR invalid settings id: %s", Short.valueOf(id));
                }
                settings.set(id, 0, value);
            }
            this.handler.settings(false, settings);
            if (settings.getHeaderTableSize() >= 0) {
                this.hpackReader.maxHeaderTableByteCountSetting(settings.getHeaderTableSize());
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readPushPromise(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (streamId == 0) {
                throw Http20Draft13.ioException("PROTOCOL_ERROR: TYPE_PUSH_PROMISE streamId == 0", new Object[0]);
            }
            short padding = (flags & 8) != 0 ? (short) (source.get() & Draft_75.END_OF_FRAME) : (short) 0;
            this.promisedStreamId = source.getInt() & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            short length2 = Http20Draft13.lengthWithoutPadding((short) (length - 4), flags, padding);
            this.pendingHeaderType = Http20Draft13.TYPE_PUSH_PROMISE;
            readHeaderBlock(source, length2, padding, flags, streamId);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readPing(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (length != 8) {
                throw Http20Draft13.ioException("TYPE_PING length != 8: %s", Short.valueOf(length));
            }
            if (streamId != 0) {
                throw Http20Draft13.ioException("TYPE_PING streamId != 0", new Object[0]);
            }
            int payload1 = source.getInt();
            int payload2 = source.getInt();
            boolean ack = (flags & 1) != 0;
            this.handler.ping(ack, payload1, payload2);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readGoAway(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (length < 8) {
                throw Http20Draft13.ioException("TYPE_GOAWAY length < 8: %s", Short.valueOf(length));
            }
            if (streamId != 0) {
                throw Http20Draft13.ioException("TYPE_GOAWAY streamId != 0", new Object[0]);
            }
            int lastStreamId = source.getInt();
            int errorCodeInt = source.getInt();
            int opaqueDataLength = length - 8;
            ErrorCode errorCode = ErrorCode.fromHttp2(errorCodeInt);
            if (errorCode == null) {
                throw Http20Draft13.ioException("TYPE_GOAWAY unexpected error code: %d", Integer.valueOf(errorCodeInt));
            }
            ByteString debugData = ByteString.EMPTY;
            if (opaqueDataLength > 0) {
                debugData = ByteString.of(source.getBytes(opaqueDataLength));
            }
            this.handler.goAway(lastStreamId, errorCode, debugData);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readWindowUpdate(ByteBufferList source, short length, byte flags, int streamId) throws IOException {
            if (length != 4) {
                throw Http20Draft13.ioException("TYPE_WINDOW_UPDATE length !=4: %s", Short.valueOf(length));
            }
            long increment = ((long) source.getInt()) & 2147483647L;
            if (increment == 0) {
                throw Http20Draft13.ioException("windowSizeIncrement was 0", Long.valueOf(increment));
            }
            this.handler.windowUpdate(streamId, increment);
        }
    }

    static final class Writer implements FrameWriter {
        private final boolean client;
        private boolean closed;
        private final ByteBufferList frameHeader = new ByteBufferList();
        private final HpackDraft08.Writer hpackWriter = new HpackDraft08.Writer();
        private final BufferedDataSink sink;

        Writer(BufferedDataSink sink, boolean client) {
            this.sink = sink;
            this.client = client;
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void ackSettings() throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            frameHeader(0, 0, (byte) 4, (byte) 1);
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void connectionPreface() throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (this.client) {
                if (Http20Draft13.logger.isLoggable(Level.FINE)) {
                    Http20Draft13.logger.fine(String.format(Locale.ENGLISH, ">> CONNECTION %s", Http20Draft13.CONNECTION_PREFACE.hex()));
                }
                this.sink.write(new ByteBufferList(Http20Draft13.CONNECTION_PREFACE.toByteArray()));
            }
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void synStream(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, List<Header> headerBlock) throws IOException {
            if (inFinished) {
                throw new UnsupportedOperationException();
            }
            if (this.closed) {
                throw new IOException("closed");
            }
            headers(outFinished, streamId, headerBlock);
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void synReply(boolean outFinished, int streamId, List<Header> headerBlock) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            headers(outFinished, streamId, headerBlock);
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void headers(int streamId, List<Header> headerBlock) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            headers(false, streamId, headerBlock);
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            ByteBufferList hpackBuffer = this.hpackWriter.writeHeaders(requestHeaders);
            long byteCount = hpackBuffer.remaining();
            int length = (int) Math.min(16379L, byteCount);
            byte flags = byteCount == ((long) length) ? (byte) 4 : (byte) 0;
            frameHeader(streamId, length + 4, Http20Draft13.TYPE_PUSH_PROMISE, flags);
            ByteBuffer sink = ByteBufferList.obtain(8192).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(Integer.MAX_VALUE & promisedStreamId);
            sink.flip();
            this.frameHeader.add(sink);
            hpackBuffer.get(this.frameHeader, length);
            this.sink.write(this.frameHeader);
            if (byteCount > length) {
                writeContinuationFrames(hpackBuffer, streamId);
            }
        }

        void headers(boolean outFinished, int streamId, List<Header> headerBlock) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            ByteBufferList hpackBuffer = this.hpackWriter.writeHeaders(headerBlock);
            long byteCount = hpackBuffer.remaining();
            int length = (int) Math.min(16383L, byteCount);
            byte flags = byteCount == ((long) length) ? (byte) 4 : (byte) 0;
            if (outFinished) {
                flags = (byte) (flags | 1);
            }
            frameHeader(streamId, length, (byte) 1, flags);
            hpackBuffer.get(this.frameHeader, length);
            this.sink.write(this.frameHeader);
            if (byteCount > length) {
                writeContinuationFrames(hpackBuffer, streamId);
            }
        }

        private void writeContinuationFrames(ByteBufferList hpackBuffer, int streamId) throws IOException {
            while (hpackBuffer.hasRemaining()) {
                int length = Math.min(Http20Draft13.MAX_FRAME_SIZE, hpackBuffer.remaining());
                int newRemaining = hpackBuffer.remaining() - length;
                frameHeader(streamId, length, Http20Draft13.TYPE_CONTINUATION, newRemaining == 0 ? (byte) 4 : (byte) 0);
                hpackBuffer.get(this.frameHeader, length);
                this.sink.write(this.frameHeader);
            }
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void rstStream(int streamId, ErrorCode errorCode) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (errorCode.spdyRstCode == -1) {
                throw new IllegalArgumentException();
            }
            frameHeader(streamId, 4, Http20Draft13.TYPE_RST_STREAM, (byte) 0);
            ByteBuffer sink = ByteBufferList.obtain(8192).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(errorCode.httpCode);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void data(boolean outFinished, int streamId, ByteBufferList source) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            byte flags = outFinished ? (byte) 1 : (byte) 0;
            dataFrame(streamId, flags, source);
        }

        void dataFrame(int streamId, byte flags, ByteBufferList buffer) throws IOException {
            frameHeader(streamId, buffer.remaining(), (byte) 0, flags);
            this.sink.write(buffer);
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void settings(Settings settings) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            int length = settings.size() * 6;
            frameHeader(0, length, (byte) 4, (byte) 0);
            ByteBuffer sink = ByteBufferList.obtain(8192).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < 10; i++) {
                if (settings.isSet(i)) {
                    int id = i;
                    if (id == 4) {
                        id = 3;
                    } else if (id == 7) {
                        id = 4;
                    }
                    sink.putShort((short) id);
                    sink.putInt(settings.get(i));
                }
            }
            sink.flip();
            this.sink.write(this.frameHeader.add(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void ping(boolean ack, int payload1, int payload2) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            byte flags = ack ? (byte) 1 : (byte) 0;
            frameHeader(0, 8, Http20Draft13.TYPE_PING, flags);
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(payload1);
            sink.putInt(payload2);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void goAway(int lastGoodStreamId, ErrorCode errorCode, byte[] debugData) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (errorCode.httpCode == -1) {
                throw Http20Draft13.illegalArgument("errorCode.httpCode == -1", new Object[0]);
            }
            int length = debugData.length + 8;
            frameHeader(0, length, Http20Draft13.TYPE_GOAWAY, (byte) 0);
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(lastGoodStreamId);
            sink.putInt(errorCode.httpCode);
            sink.put(debugData);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void windowUpdate(int streamId, long windowSizeIncrement) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (windowSizeIncrement == 0 || windowSizeIncrement > 2147483647L) {
                throw Http20Draft13.illegalArgument("windowSizeIncrement == 0 || windowSizeIncrement > 0x7fffffffL: %s", Long.valueOf(windowSizeIncrement));
            }
            frameHeader(streamId, 4, (byte) 8, (byte) 0);
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt((int) windowSizeIncrement);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink));
        }

        @Override // java.io.Closeable, java.lang.AutoCloseable
        public synchronized void close() throws IOException {
            this.closed = true;
        }

        void frameHeader(int streamId, int length, byte type, byte flags) throws IOException {
            if (Http20Draft13.logger.isLoggable(Level.FINE)) {
                Http20Draft13.logger.fine(FrameLogger.formatHeader(false, streamId, length, type, flags));
            }
            if (length > Http20Draft13.MAX_FRAME_SIZE) {
                throw Http20Draft13.illegalArgument("FRAME_SIZE_ERROR length > %d: %d", Integer.valueOf(Http20Draft13.MAX_FRAME_SIZE), Integer.valueOf(length));
            }
            if ((Integer.MIN_VALUE & streamId) != 0) {
                throw Http20Draft13.illegalArgument("reserved bit set: %s", Integer.valueOf(streamId));
            }
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(((length & Http20Draft13.MAX_FRAME_SIZE) << 16) | ((type & Draft_75.END_OF_FRAME) << 8) | (flags & Draft_75.END_OF_FRAME));
            sink.putInt(Integer.MAX_VALUE & streamId);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static IllegalArgumentException illegalArgument(String message, Object... args) {
        throw new IllegalArgumentException(String.format(Locale.ENGLISH, message, args));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static IOException ioException(String message, Object... args) throws IOException {
        throw new IOException(String.format(Locale.ENGLISH, message, args));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static short lengthWithoutPadding(short length, byte flags, short padding) throws IOException {
        if ((flags & 8) != 0) {
            length = (short) (length - 1);
        }
        if (padding > length) {
            throw ioException("PROTOCOL_ERROR padding %s > remaining length %s", Short.valueOf(padding), Short.valueOf(length));
        }
        return (short) (length - padding);
    }

    static final class FrameLogger {
        private static final String[] TYPES = {"DATA", "HEADERS", "PRIORITY", "RST_STREAM", "SETTINGS", "PUSH_PROMISE", "PING", "GOAWAY", "WINDOW_UPDATE", "CONTINUATION"};
        private static final String[] FLAGS = new String[64];
        private static final String[] BINARY = new String[256];

        FrameLogger() {
        }

        static String formatHeader(boolean inbound, int streamId, int length, byte type, byte flags) {
            String formattedType = type < TYPES.length ? TYPES[type] : String.format(Locale.ENGLISH, "0x%02x", Byte.valueOf(type));
            String formattedFlags = formatFlags(type, flags);
            Locale locale = Locale.ENGLISH;
            Object[] objArr = new Object[5];
            objArr[0] = inbound ? "<<" : ">>";
            objArr[1] = Integer.valueOf(streamId);
            objArr[2] = Integer.valueOf(length);
            objArr[3] = formattedType;
            objArr[4] = formattedFlags;
            return String.format(locale, "%s 0x%08x %5d %-13s %s", objArr);
        }

        static String formatFlags(byte type, byte flags) {
            if (flags == 0) {
                return "";
            }
            switch (type) {
                case 2:
                case 3:
                case 7:
                case 8:
                    return BINARY[flags];
                case 4:
                case 6:
                    return flags == 1 ? "ACK" : BINARY[flags];
                case 5:
                default:
                    String result = flags < FLAGS.length ? FLAGS[flags] : BINARY[flags];
                    if (type != 5 || (flags & 4) == 0) {
                        return (type != 0 || (flags & 32) == 0) ? result : result.replace("PRIORITY", "COMPRESSED");
                    }
                    return result.replace("HEADERS", "PUSH_PROMISE");
            }
        }

        static {
            for (int i = 0; i < BINARY.length; i++) {
                BINARY[i] = String.format(Locale.ENGLISH, "%8s", Integer.toBinaryString(i)).replace(' ', '0');
            }
            FLAGS[0] = "";
            FLAGS[1] = "END_STREAM";
            FLAGS[2] = "END_SEGMENT";
            FLAGS[3] = "END_STREAM|END_SEGMENT";
            int[] prefixFlags = {1, 2, 3};
            FLAGS[8] = "PADDED";
            for (int prefixFlag : prefixFlags) {
                FLAGS[prefixFlag | 8] = FLAGS[prefixFlag] + "|PADDED";
            }
            FLAGS[4] = "END_HEADERS";
            FLAGS[32] = "PRIORITY";
            FLAGS[36] = "END_HEADERS|PRIORITY";
            int[] frameFlags = {4, 32, 36};
            for (int frameFlag : frameFlags) {
                for (int prefixFlag2 : prefixFlags) {
                    FLAGS[prefixFlag2 | frameFlag] = FLAGS[prefixFlag2] + '|' + FLAGS[frameFlag];
                    FLAGS[prefixFlag2 | frameFlag | 8] = FLAGS[prefixFlag2] + '|' + FLAGS[frameFlag] + "|PADDED";
                }
            }
            for (int i2 = 0; i2 < FLAGS.length; i2++) {
                if (FLAGS[i2] == null) {
                    FLAGS[i2] = BINARY[i2];
                }
            }
        }
    }
}
