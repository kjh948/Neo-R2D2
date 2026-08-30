package com.koushikdutta.async.http.spdy;

import android.os.Build;
import android.support.v4.internal.view.SupportMenu;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.ActivityChooserView;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataEmitterReader;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.spdy.FrameReader;
import com.koushikdutta.async.util.Charsets;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;

/* JADX INFO: loaded from: classes.dex */
final class Spdy3 implements Variant {
    static final byte[] DICTIONARY;
    static final int FLAG_FIN = 1;
    static final int FLAG_UNIDIRECTIONAL = 2;
    static final int TYPE_DATA = 0;
    static final int TYPE_GOAWAY = 7;
    static final int TYPE_HEADERS = 8;
    static final int TYPE_PING = 6;
    static final int TYPE_RST_STREAM = 3;
    static final int TYPE_SETTINGS = 4;
    static final int TYPE_SYN_REPLY = 2;
    static final int TYPE_SYN_STREAM = 1;
    static final int TYPE_WINDOW_UPDATE = 9;
    static final int VERSION = 3;

    Spdy3() {
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public Protocol getProtocol() {
        return Protocol.SPDY_3;
    }

    static {
        try {
            DICTIONARY = "\u0000\u0000\u0000\u0007options\u0000\u0000\u0000\u0004head\u0000\u0000\u0000\u0004post\u0000\u0000\u0000\u0003put\u0000\u0000\u0000\u0006delete\u0000\u0000\u0000\u0005trace\u0000\u0000\u0000\u0006accept\u0000\u0000\u0000\u000eaccept-charset\u0000\u0000\u0000\u000faccept-encoding\u0000\u0000\u0000\u000faccept-language\u0000\u0000\u0000\raccept-ranges\u0000\u0000\u0000\u0003age\u0000\u0000\u0000\u0005allow\u0000\u0000\u0000\rauthorization\u0000\u0000\u0000\rcache-control\u0000\u0000\u0000\nconnection\u0000\u0000\u0000\fcontent-base\u0000\u0000\u0000\u0010content-encoding\u0000\u0000\u0000\u0010content-language\u0000\u0000\u0000\u000econtent-length\u0000\u0000\u0000\u0010content-location\u0000\u0000\u0000\u000bcontent-md5\u0000\u0000\u0000\rcontent-range\u0000\u0000\u0000\fcontent-type\u0000\u0000\u0000\u0004date\u0000\u0000\u0000\u0004etag\u0000\u0000\u0000\u0006expect\u0000\u0000\u0000\u0007expires\u0000\u0000\u0000\u0004from\u0000\u0000\u0000\u0004host\u0000\u0000\u0000\bif-match\u0000\u0000\u0000\u0011if-modified-since\u0000\u0000\u0000\rif-none-match\u0000\u0000\u0000\bif-range\u0000\u0000\u0000\u0013if-unmodified-since\u0000\u0000\u0000\rlast-modified\u0000\u0000\u0000\blocation\u0000\u0000\u0000\fmax-forwards\u0000\u0000\u0000\u0006pragma\u0000\u0000\u0000\u0012proxy-authenticate\u0000\u0000\u0000\u0013proxy-authorization\u0000\u0000\u0000\u0005range\u0000\u0000\u0000\u0007referer\u0000\u0000\u0000\u000bretry-after\u0000\u0000\u0000\u0006server\u0000\u0000\u0000\u0002te\u0000\u0000\u0000\u0007trailer\u0000\u0000\u0000\u0011transfer-encoding\u0000\u0000\u0000\u0007upgrade\u0000\u0000\u0000\nuser-agent\u0000\u0000\u0000\u0004vary\u0000\u0000\u0000\u0003via\u0000\u0000\u0000\u0007warning\u0000\u0000\u0000\u0010www-authenticate\u0000\u0000\u0000\u0006method\u0000\u0000\u0000\u0003get\u0000\u0000\u0000\u0006status\u0000\u0000\u0000\u0006200 OK\u0000\u0000\u0000\u0007version\u0000\u0000\u0000\bHTTP/1.1\u0000\u0000\u0000\u0003url\u0000\u0000\u0000\u0006public\u0000\u0000\u0000\nset-cookie\u0000\u0000\u0000\nkeep-alive\u0000\u0000\u0000\u0006origin100101201202205206300302303304305306307402405406407408409410411412413414415416417502504505203 Non-Authoritative Information204 No Content301 Moved Permanently400 Bad Request401 Unauthorized403 Forbidden404 Not Found500 Internal Server Error501 Not Implemented503 Service UnavailableJan Feb Mar Apr May Jun Jul Aug Sept Oct Nov Dec 00:00:00 Mon, Tue, Wed, Thu, Fri, Sat, Sun, GMTchunked,text/html,image/png,image/jpg,image/gif,application/xml,application/xhtml+xml,text/plain,text/javascript,publicprivatemax-age=gzip,deflate,sdchcharset=utf-8charset=iso-8859-1,utf-,*,enq=0.".getBytes(Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError();
        }
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public FrameReader newReader(DataEmitter source, FrameReader.Handler handler, boolean client) {
        return new Reader(source, handler, client);
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public FrameWriter newWriter(BufferedDataSink sink, boolean client) {
        return new Writer(sink, client);
    }

    @Override // com.koushikdutta.async.http.spdy.Variant
    public int maxFrameSize() {
        return 16383;
    }

    static final class Reader implements FrameReader {
        private final boolean client;
        private final DataEmitter emitter;
        int flags;
        private final FrameReader.Handler handler;
        boolean inFinished;
        int length;
        private final DataEmitterReader reader;
        int streamId;
        int w1;
        int w2;
        private final HeaderReader headerReader = new HeaderReader();
        private final ByteBufferList emptyList = new ByteBufferList();
        private final DataCallback onFrame = new DataCallback() { // from class: com.koushikdutta.async.http.spdy.Spdy3.Reader.2
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.order(ByteOrder.BIG_ENDIAN);
                Reader.this.w1 = bb.getInt();
                Reader.this.w2 = bb.getInt();
                boolean control = (Reader.this.w1 & Integer.MIN_VALUE) != 0;
                Reader.this.flags = (Reader.this.w2 & ViewCompat.MEASURED_STATE_MASK) >>> 24;
                Reader.this.length = Reader.this.w2 & ViewCompat.MEASURED_SIZE_MASK;
                if (control) {
                    Reader.this.reader.read(Reader.this.length, Reader.this.onFullFrame);
                    return;
                }
                Reader.this.streamId = Reader.this.w1 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
                Reader.this.inFinished = (Reader.this.flags & 1) != 0;
                emitter.setDataCallback(Reader.this.onDataFrame);
                if (Reader.this.length == 0) {
                    Reader.this.onDataFrame.onDataAvailable(emitter, Reader.this.emptyList);
                }
            }
        };
        ByteBufferList partial = new ByteBufferList();
        private final DataCallback onDataFrame = new DataCallback() { // from class: com.koushikdutta.async.http.spdy.Spdy3.Reader.3
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                int toRead = Math.min(bb.remaining(), Reader.this.length);
                if (toRead < bb.remaining()) {
                    bb.get(Reader.this.partial, toRead);
                    bb = Reader.this.partial;
                }
                Reader.this.length -= toRead;
                Reader.this.handler.data(Reader.this.length == 0 && Reader.this.inFinished, Reader.this.streamId, bb);
                if (Reader.this.length == 0) {
                    Reader.this.parseFrameHeader();
                }
            }
        };
        private final DataCallback onFullFrame = new DataCallback() { // from class: com.koushikdutta.async.http.spdy.Spdy3.Reader.4
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                bb.order(ByteOrder.BIG_ENDIAN);
                int version = (Reader.this.w1 & 2147418112) >>> 16;
                int type = Reader.this.w1 & SupportMenu.USER_MASK;
                try {
                    if (version == 3) {
                        switch (type) {
                            case 1:
                                Reader.this.readSynStream(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 2:
                                Reader.this.readSynReply(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 3:
                                Reader.this.readRstStream(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 4:
                                Reader.this.readSettings(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 5:
                            default:
                                bb.recycle();
                                break;
                            case 6:
                                Reader.this.readPing(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 7:
                                Reader.this.readGoAway(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 8:
                                Reader.this.readHeaders(bb, Reader.this.flags, Reader.this.length);
                                break;
                            case 9:
                                Reader.this.readWindowUpdate(bb, Reader.this.flags, Reader.this.length);
                                break;
                        }
                        Reader.this.parseFrameHeader();
                        return;
                    }
                    throw new ProtocolException("version != 3: " + version);
                } catch (IOException e) {
                    Reader.this.handler.error(e);
                }
            }
        };

        Reader(DataEmitter emitter, FrameReader.Handler handler, boolean client) {
            this.emitter = emitter;
            this.handler = handler;
            this.client = client;
            emitter.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.spdy.Spdy3.Reader.1
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                }
            });
            this.reader = new DataEmitterReader();
            parseFrameHeader();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void parseFrameHeader() {
            this.emitter.setDataCallback(this.reader);
            this.reader.read(8, this.onFrame);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readSynStream(ByteBufferList source, int flags, int length) throws IOException {
            int w1 = source.getInt();
            int w2 = source.getInt();
            int streamId = w1 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            int associatedStreamId = w2 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            source.getShort();
            List<Header> headerBlock = this.headerReader.readHeader(source, length - 10);
            boolean inFinished = (flags & 1) != 0;
            boolean outFinished = (flags & 2) != 0;
            this.handler.headers(outFinished, inFinished, streamId, associatedStreamId, headerBlock, HeadersMode.SPDY_SYN_STREAM);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readSynReply(ByteBufferList source, int flags, int length) throws IOException {
            int w1 = source.getInt();
            int streamId = w1 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            List<Header> headerBlock = this.headerReader.readHeader(source, length - 4);
            boolean inFinished = (flags & 1) != 0;
            this.handler.headers(false, inFinished, streamId, -1, headerBlock, HeadersMode.SPDY_REPLY);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readRstStream(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 8) {
                throw ioException("TYPE_RST_STREAM length: %d != 8", Integer.valueOf(length));
            }
            int streamId = source.getInt() & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            int errorCodeInt = source.getInt();
            ErrorCode errorCode = ErrorCode.fromSpdy3Rst(errorCodeInt);
            if (errorCode == null) {
                throw ioException("TYPE_RST_STREAM unexpected error code: %d", Integer.valueOf(errorCodeInt));
            }
            this.handler.rstStream(streamId, errorCode);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readHeaders(ByteBufferList source, int flags, int length) throws IOException {
            int w1 = source.getInt();
            int streamId = w1 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            List<Header> headerBlock = this.headerReader.readHeader(source, length - 4);
            this.handler.headers(false, false, streamId, -1, headerBlock, HeadersMode.SPDY_HEADERS);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readWindowUpdate(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 8) {
                throw ioException("TYPE_WINDOW_UPDATE length: %d != 8", Integer.valueOf(length));
            }
            int w1 = source.getInt();
            int w2 = source.getInt();
            int streamId = w1 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            long increment = w2 & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            if (increment == 0) {
                throw ioException("windowSizeIncrement was 0", Long.valueOf(increment));
            }
            this.handler.windowUpdate(streamId, increment);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readPing(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 4) {
                throw ioException("TYPE_PING length: %d != 4", Integer.valueOf(length));
            }
            int id = source.getInt();
            boolean ack = this.client == ((id & 1) == 1);
            this.handler.ping(ack, id, 0);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readGoAway(ByteBufferList source, int flags, int length) throws IOException {
            if (length != 8) {
                throw ioException("TYPE_GOAWAY length: %d != 8", Integer.valueOf(length));
            }
            int lastGoodStreamId = source.getInt() & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;
            int errorCodeInt = source.getInt();
            ErrorCode errorCode = ErrorCode.fromSpdyGoAway(errorCodeInt);
            if (errorCode == null) {
                throw ioException("TYPE_GOAWAY unexpected error code: %d", Integer.valueOf(errorCodeInt));
            }
            this.handler.goAway(lastGoodStreamId, errorCode, ByteString.EMPTY);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void readSettings(ByteBufferList source, int flags, int length) throws IOException {
            int numberOfEntries = source.getInt();
            if (length != (numberOfEntries * 8) + 4) {
                throw ioException("TYPE_SETTINGS length: %d != 4 + 8 * %d", Integer.valueOf(length), Integer.valueOf(numberOfEntries));
            }
            Settings settings = new Settings();
            for (int i = 0; i < numberOfEntries; i++) {
                int w1 = source.getInt();
                int value = source.getInt();
                int idFlags = ((-16777216) & w1) >>> 24;
                int id = w1 & ViewCompat.MEASURED_SIZE_MASK;
                settings.set(id, idFlags, value);
            }
            boolean clearPrevious = (flags & 1) != 0;
            this.handler.settings(clearPrevious, settings);
        }

        private static IOException ioException(String message, Object... args) throws IOException {
            throw new IOException(String.format(Locale.ENGLISH, message, args));
        }
    }

    static final class Writer implements FrameWriter {
        private final boolean client;
        private boolean closed;
        private final BufferedDataSink sink;
        private ByteBufferList frameHeader = new ByteBufferList();
        private final Deflater deflater = new Deflater();
        ByteBufferList dataList = new ByteBufferList();
        ByteBufferList headerBlockList = new ByteBufferList();

        Writer(BufferedDataSink sink, boolean client) {
            this.sink = sink;
            this.client = client;
            this.deflater.setDictionary(Spdy3.DICTIONARY);
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public void ackSettings() {
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders) throws IOException {
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void connectionPreface() {
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void synStream(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, List<Header> headerBlock) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            ByteBufferList headerBlockBuffer = writeNameValueBlockToBuffer(headerBlock);
            int length = headerBlockBuffer.remaining() + 10;
            int flags = (outFinished ? 1 : 0) | (inFinished ? 2 : 0);
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287039);
            sink.putInt(((flags & 255) << 24) | (16777215 & length));
            sink.putInt(streamId & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
            sink.putInt(associatedStreamId & ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
            sink.putShort((short) 0);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink).add(headerBlockBuffer));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void synReply(boolean outFinished, int streamId, List<Header> headerBlock) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            ByteBufferList headerBlockBuffer = writeNameValueBlockToBuffer(headerBlock);
            int flags = outFinished ? 1 : 0;
            int length = headerBlockBuffer.remaining() + 4;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287038);
            sink.putInt(((flags & 255) << 24) | (16777215 & length));
            sink.putInt(Integer.MAX_VALUE & streamId);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink).add(headerBlockBuffer));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void headers(int streamId, List<Header> headerBlock) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            ByteBufferList headerBlockBuffer = writeNameValueBlockToBuffer(headerBlock);
            int length = headerBlockBuffer.remaining() + 4;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287032);
            sink.putInt((16777215 & length) | 0);
            sink.putInt(Integer.MAX_VALUE & streamId);
            sink.flip();
            this.sink.write(this.frameHeader.add(sink).add(headerBlockBuffer));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void rstStream(int streamId, ErrorCode errorCode) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (errorCode.spdyRstCode == -1) {
                throw new IllegalArgumentException();
            }
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287037);
            sink.putInt(8);
            sink.putInt(Integer.MAX_VALUE & streamId);
            sink.putInt(errorCode.spdyRstCode);
            sink.flip();
            this.sink.write(this.frameHeader.addAll(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void data(boolean outFinished, int streamId, ByteBufferList source) throws IOException {
            int flags = outFinished ? 1 : 0;
            sendDataFrame(streamId, flags, source);
        }

        void sendDataFrame(int streamId, int flags, ByteBufferList buffer) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            int byteCount = buffer.remaining();
            if (byteCount > 16777215) {
                throw new IllegalArgumentException("FRAME_TOO_LARGE max size is 16Mib: " + byteCount);
            }
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(Integer.MAX_VALUE & streamId);
            sink.putInt(((flags & 255) << 24) | (16777215 & byteCount));
            sink.flip();
            this.dataList.add(sink).add(buffer);
            this.sink.write(this.dataList);
        }

        private ByteBufferList writeNameValueBlockToBuffer(List<Header> headerBlock) throws IOException {
            int read;
            if (this.headerBlockList.hasRemaining()) {
                throw new IllegalStateException();
            }
            ByteBuffer headerBlockOut = ByteBufferList.obtain(8192).order(ByteOrder.BIG_ENDIAN);
            headerBlockOut.putInt(headerBlock.size());
            int size = headerBlock.size();
            for (int i = 0; i < size; i++) {
                ByteString name = headerBlock.get(i).name;
                headerBlockOut.putInt(name.size());
                headerBlockOut.put(name.toByteArray());
                ByteString value = headerBlock.get(i).value;
                headerBlockOut.putInt(value.size());
                headerBlockOut.put(value.toByteArray());
                if (headerBlockOut.remaining() < headerBlockOut.capacity() / 2) {
                    ByteBuffer newOut = ByteBufferList.obtain(headerBlockOut.capacity() * 2).order(ByteOrder.BIG_ENDIAN);
                    headerBlockOut.flip();
                    newOut.put(headerBlockOut);
                    ByteBufferList.reclaim(headerBlockOut);
                    headerBlockOut = newOut;
                }
            }
            headerBlockOut.flip();
            this.deflater.setInput(headerBlockOut.array(), 0, headerBlockOut.remaining());
            while (!this.deflater.needsInput()) {
                ByteBuffer deflated = ByteBufferList.obtain(headerBlockOut.capacity()).order(ByteOrder.BIG_ENDIAN);
                if (Build.VERSION.SDK_INT >= 19) {
                    read = this.deflater.deflate(deflated.array(), 0, deflated.capacity(), 2);
                } else {
                    read = this.deflater.deflate(deflated.array(), 0, deflated.capacity());
                }
                deflated.limit(read);
                this.headerBlockList.add(deflated);
            }
            ByteBufferList.reclaim(headerBlockOut);
            return this.headerBlockList;
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void settings(Settings settings) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            int size = settings.size();
            int length = (size * 8) + 4;
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287036);
            sink.putInt((length & ViewCompat.MEASURED_SIZE_MASK) | 0);
            sink.putInt(size);
            for (int i = 0; i <= 10; i++) {
                if (settings.isSet(i)) {
                    int settingsFlags = settings.flags(i);
                    sink.putInt(((settingsFlags & 255) << 24) | (i & ViewCompat.MEASURED_SIZE_MASK));
                    sink.putInt(settings.get(i));
                }
            }
            sink.flip();
            this.sink.write(this.frameHeader.addAll(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void ping(boolean reply, int payload1, int payload2) throws IOException {
            synchronized (this) {
                if (this.closed) {
                    throw new IOException("closed");
                }
                boolean payloadIsReply = this.client != ((payload1 & 1) == 1);
                if (reply != payloadIsReply) {
                    throw new IllegalArgumentException("payload != reply");
                }
                ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
                sink.putInt(-2147287034);
                sink.putInt(4);
                sink.putInt(payload1);
                sink.flip();
                this.sink.write(this.frameHeader.addAll(sink));
            }
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void goAway(int lastGoodStreamId, ErrorCode errorCode, byte[] ignored) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (errorCode.spdyGoAwayCode == -1) {
                throw new IllegalArgumentException("errorCode.spdyGoAwayCode == -1");
            }
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287033);
            sink.putInt(8);
            sink.putInt(lastGoodStreamId);
            sink.putInt(errorCode.spdyGoAwayCode);
            sink.flip();
            this.sink.write(this.frameHeader.addAll(sink));
        }

        @Override // com.koushikdutta.async.http.spdy.FrameWriter
        public synchronized void windowUpdate(int streamId, long increment) throws IOException {
            if (this.closed) {
                throw new IOException("closed");
            }
            if (increment == 0 || increment > 2147483647L) {
                throw new IllegalArgumentException("windowSizeIncrement must be between 1 and 0x7fffffff: " + increment);
            }
            ByteBuffer sink = ByteBufferList.obtain(256).order(ByteOrder.BIG_ENDIAN);
            sink.putInt(-2147287031);
            sink.putInt(8);
            sink.putInt(streamId);
            sink.putInt((int) increment);
            sink.flip();
            this.sink.write(this.frameHeader.addAll(sink));
        }

        @Override // java.io.Closeable, java.lang.AutoCloseable
        public synchronized void close() throws IOException {
            this.closed = true;
        }
    }
}
