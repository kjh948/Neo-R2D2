package com.koushikdutta.async.stream;

import com.koushikdutta.async.ByteBufferList;
import java.io.IOException;
import java.io.InputStream;

/* JADX INFO: loaded from: classes.dex */
public class ByteBufferListInputStream extends InputStream {
    ByteBufferList bb;

    public ByteBufferListInputStream(ByteBufferList bb) {
        this.bb = bb;
    }

    @Override // java.io.InputStream
    public int read() throws IOException {
        if (this.bb.remaining() <= 0) {
            return -1;
        }
        return this.bb.get();
    }

    @Override // java.io.InputStream
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override // java.io.InputStream
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (this.bb.remaining() <= 0) {
            return -1;
        }
        int toRead = Math.min(length, this.bb.remaining());
        this.bb.get(buffer, offset, toRead);
        return toRead;
    }
}
