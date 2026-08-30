package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.PushParser;
import com.koushikdutta.async.callback.DataCallback;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import org.java_websocket.drafts.Draft_75;

/* JADX INFO: loaded from: classes.dex */
public class GZIPInputFilter extends InflaterInputFilter {
    private static final int FCOMMENT = 16;
    private static final int FEXTRA = 4;
    private static final int FHCRC = 2;
    private static final int FNAME = 8;
    protected CRC32 crc;
    boolean mNeedsHeader;

    static short peekShort(byte[] src, int offset, ByteOrder order) {
        return order == ByteOrder.BIG_ENDIAN ? (short) ((src[offset] << 8) | (src[offset + 1] & Draft_75.END_OF_FRAME)) : (short) ((src[offset + 1] << 8) | (src[offset] & Draft_75.END_OF_FRAME));
    }

    public GZIPInputFilter() {
        super(new Inflater(true));
        this.mNeedsHeader = true;
        this.crc = new CRC32();
    }

    public static int unsignedToBytes(byte b) {
        return b & Draft_75.END_OF_FRAME;
    }

    @Override // com.koushikdutta.async.http.filter.InflaterInputFilter, com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (this.mNeedsHeader) {
            PushParser parser = new PushParser(emitter);
            parser.readByteArray(10, new AnonymousClass1(emitter, parser));
        } else {
            super.onDataAvailable(emitter, bb);
        }
    }

    /* JADX INFO: renamed from: com.koushikdutta.async.http.filter.GZIPInputFilter$1, reason: invalid class name */
    class AnonymousClass1 implements PushParser.ParseCallback<byte[]> {
        int flags;
        boolean hcrc;
        final /* synthetic */ DataEmitter val$emitter;
        final /* synthetic */ PushParser val$parser;

        AnonymousClass1(DataEmitter dataEmitter, PushParser pushParser) {
            this.val$emitter = dataEmitter;
            this.val$parser = pushParser;
        }

        @Override // com.koushikdutta.async.PushParser.ParseCallback
        public void parsed(byte[] header) {
            short magic = GZIPInputFilter.peekShort(header, 0, ByteOrder.LITTLE_ENDIAN);
            if (magic != -29921) {
                GZIPInputFilter.this.report(new IOException(String.format(Locale.ENGLISH, "unknown format (magic number %x)", Short.valueOf(magic))));
                this.val$emitter.setDataCallback(new DataCallback.NullDataCallback());
                return;
            }
            this.flags = header[3];
            this.hcrc = (this.flags & 2) != 0;
            if (this.hcrc) {
                GZIPInputFilter.this.crc.update(header, 0, header.length);
            }
            if ((this.flags & 4) != 0) {
                this.val$parser.readByteArray(2, new PushParser.ParseCallback<byte[]>() { // from class: com.koushikdutta.async.http.filter.GZIPInputFilter.1.1
                    @Override // com.koushikdutta.async.PushParser.ParseCallback
                    public void parsed(byte[] header2) {
                        if (AnonymousClass1.this.hcrc) {
                            GZIPInputFilter.this.crc.update(header2, 0, 2);
                        }
                        int length = GZIPInputFilter.peekShort(header2, 0, ByteOrder.LITTLE_ENDIAN) & 65535;
                        AnonymousClass1.this.val$parser.readByteArray(length, new PushParser.ParseCallback<byte[]>() { // from class: com.koushikdutta.async.http.filter.GZIPInputFilter.1.1.1
                            @Override // com.koushikdutta.async.PushParser.ParseCallback
                            public void parsed(byte[] buf) {
                                if (AnonymousClass1.this.hcrc) {
                                    GZIPInputFilter.this.crc.update(buf, 0, buf.length);
                                }
                                AnonymousClass1.this.next();
                            }
                        });
                    }
                });
            } else {
                next();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void next() {
            PushParser parser = new PushParser(this.val$emitter);
            DataCallback summer = new DataCallback() { // from class: com.koushikdutta.async.http.filter.GZIPInputFilter.1.2
                @Override // com.koushikdutta.async.callback.DataCallback
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    if (AnonymousClass1.this.hcrc) {
                        while (bb.size() > 0) {
                            ByteBuffer b = bb.remove();
                            GZIPInputFilter.this.crc.update(b.array(), b.arrayOffset() + b.position(), b.remaining());
                            ByteBufferList.reclaim(b);
                        }
                    }
                    bb.recycle();
                    AnonymousClass1.this.done();
                }
            };
            if ((this.flags & 8) != 0) {
                parser.until((byte) 0, summer);
            } else if ((this.flags & 16) != 0) {
                parser.until((byte) 0, summer);
            } else {
                done();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void done() {
            if (this.hcrc) {
                this.val$parser.readByteArray(2, new PushParser.ParseCallback<byte[]>() { // from class: com.koushikdutta.async.http.filter.GZIPInputFilter.1.3
                    @Override // com.koushikdutta.async.PushParser.ParseCallback
                    public void parsed(byte[] header) {
                        short crc16 = GZIPInputFilter.peekShort(header, 0, ByteOrder.LITTLE_ENDIAN);
                        if (((short) GZIPInputFilter.this.crc.getValue()) != crc16) {
                            GZIPInputFilter.this.report(new IOException("CRC mismatch"));
                            return;
                        }
                        GZIPInputFilter.this.crc.reset();
                        GZIPInputFilter.this.mNeedsHeader = false;
                        GZIPInputFilter.this.setDataEmitter(AnonymousClass1.this.val$emitter);
                    }
                });
            } else {
                GZIPInputFilter.this.mNeedsHeader = false;
                GZIPInputFilter.this.setDataEmitter(this.val$emitter);
            }
        }
    }
}
