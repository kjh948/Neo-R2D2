package com.koushikdutta.async.http.cache;

import com.koushikdutta.async.util.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/* JADX INFO: loaded from: classes.dex */
class StrictLineReader implements Closeable {
    private static final byte CR = 13;
    private static final byte LF = 10;
    private byte[] buf;
    private int end;
    private final InputStream in;
    private int pos;

    public StrictLineReader(InputStream in) {
        this(in, 8192);
    }

    public StrictLineReader(InputStream in, int capacity) {
        this(in, capacity, Charsets.US_ASCII);
    }

    public StrictLineReader(InputStream in, Charset charset) {
        this(in, 8192, charset);
    }

    public StrictLineReader(InputStream in, int capacity, Charset charset) {
        if (in == null) {
            throw new NullPointerException("in == null");
        }
        if (charset == null) {
            throw new NullPointerException("charset == null");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity <= 0");
        }
        if (!charset.equals(Charsets.US_ASCII) && !charset.equals(Charsets.UTF_8)) {
            throw new IllegalArgumentException("Unsupported encoding");
        }
        this.in = in;
        this.buf = new byte[capacity];
    }

    @Override // java.io.Closeable, java.lang.AutoCloseable
    public void close() throws IOException {
        synchronized (this.in) {
            if (this.buf != null) {
                this.buf = null;
                this.in.close();
            }
        }
    }

    public String readLine() throws IOException {
        int i;
        String res;
        synchronized (this.in) {
            if (this.buf == null) {
                throw new IOException("LineReader is closed");
            }
            if (this.pos >= this.end) {
                fillBuf();
            }
            int i2 = this.pos;
            while (true) {
                if (i2 != this.end) {
                    if (this.buf[i2] != 10) {
                        i2++;
                    } else {
                        int lineEnd = (i2 == this.pos || this.buf[i2 + (-1)] != 13) ? i2 : i2 - 1;
                        res = new String(this.buf, this.pos, lineEnd - this.pos);
                        this.pos = i2 + 1;
                    }
                } else {
                    ByteArrayOutputStream out = new ByteArrayOutputStream((this.end - this.pos) + 80) { // from class: com.koushikdutta.async.http.cache.StrictLineReader.1
                        @Override // java.io.ByteArrayOutputStream
                        public String toString() {
                            int length = (this.count <= 0 || this.buf[this.count + (-1)] != 13) ? this.count : this.count - 1;
                            return new String(this.buf, 0, length);
                        }
                    };
                    loop1: while (true) {
                        out.write(this.buf, this.pos, this.end - this.pos);
                        this.end = -1;
                        fillBuf();
                        i = this.pos;
                        while (i != this.end) {
                            if (this.buf[i] == 10) {
                                break loop1;
                            }
                            i++;
                        }
                    }
                    if (i != this.pos) {
                        out.write(this.buf, this.pos, i - this.pos);
                    }
                    this.pos = i + 1;
                    res = out.toString();
                }
            }
            return res;
        }
    }

    public int readInt() throws IOException {
        String intString = readLine();
        try {
            return Integer.parseInt(intString);
        } catch (NumberFormatException e) {
            throw new IOException("expected an int but was \"" + intString + "\"");
        }
    }

    public boolean hasUnterminatedLine() {
        return this.end == -1;
    }

    private void fillBuf() throws IOException {
        int result = this.in.read(this.buf, 0, this.buf.length);
        if (result == -1) {
            throw new EOFException();
        }
        this.pos = 0;
        this.end = result;
    }
}
