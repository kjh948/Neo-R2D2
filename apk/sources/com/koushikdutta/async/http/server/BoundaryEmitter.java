package com.koushikdutta.async.http.server;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class BoundaryEmitter extends FilteredDataEmitter {
    static final /* synthetic */ boolean $assertionsDisabled;
    private byte[] boundary;
    int state = 2;

    static {
        $assertionsDisabled = !BoundaryEmitter.class.desiredAssertionStatus();
    }

    public void setBoundary(String boundary) {
        this.boundary = ("\r\n--" + boundary).getBytes();
    }

    public String getBoundary() {
        if (this.boundary == null) {
            return null;
        }
        return new String(this.boundary, 4, this.boundary.length - 4);
    }

    public String getBoundaryStart() {
        if ($assertionsDisabled || this.boundary != null) {
            return new String(this.boundary, 2, this.boundary.length - 2);
        }
        throw new AssertionError();
    }

    public String getBoundaryEnd() {
        if ($assertionsDisabled || this.boundary != null) {
            return getBoundaryStart() + "--\r\n";
        }
        throw new AssertionError();
    }

    protected void onBoundaryStart() {
    }

    protected void onBoundaryEnd() {
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (this.state > 0) {
            ByteBuffer b = ByteBufferList.obtain(this.boundary.length);
            b.put(this.boundary, 0, this.state);
            b.flip();
            bb.addFirst(b);
            this.state = 0;
        }
        int last = 0;
        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        int i = 0;
        while (i < buf.length) {
            if (this.state >= 0) {
                if (buf[i] == this.boundary[this.state]) {
                    this.state++;
                    if (this.state == this.boundary.length) {
                        this.state = -1;
                    }
                } else if (this.state > 0) {
                    i -= this.state;
                    this.state = 0;
                }
            } else if (this.state == -1) {
                if (buf[i] == 13) {
                    this.state = -4;
                    int len = (i - last) - this.boundary.length;
                    if (last != 0 || len != 0) {
                        ByteBuffer b2 = ByteBufferList.obtain(len).put(buf, last, len);
                        b2.flip();
                        ByteBufferList list = new ByteBufferList();
                        list.add(b2);
                        super.onDataAvailable(this, list);
                    }
                    onBoundaryStart();
                } else if (buf[i] == 45) {
                    this.state = -2;
                } else {
                    report(new MimeEncodingException("Invalid multipart/form-data. Expected \r or -"));
                    return;
                }
            } else if (this.state == -2) {
                if (buf[i] == 45) {
                    this.state = -3;
                } else {
                    report(new MimeEncodingException("Invalid multipart/form-data. Expected -"));
                    return;
                }
            } else if (this.state == -3) {
                if (buf[i] == 13) {
                    this.state = -4;
                    ByteBuffer b3 = ByteBufferList.obtain(((i - last) - this.boundary.length) - 2).put(buf, last, ((i - last) - this.boundary.length) - 2);
                    b3.flip();
                    ByteBufferList list2 = new ByteBufferList();
                    list2.add(b3);
                    super.onDataAvailable(this, list2);
                    onBoundaryEnd();
                } else {
                    report(new MimeEncodingException("Invalid multipart/form-data. Expected \r"));
                    return;
                }
            } else if (this.state == -4) {
                if (buf[i] == 10) {
                    last = i + 1;
                    this.state = 0;
                } else {
                    report(new MimeEncodingException("Invalid multipart/form-data. Expected \n"));
                }
            } else {
                if (!$assertionsDisabled) {
                    throw new AssertionError();
                }
                report(new MimeEncodingException("Invalid multipart/form-data. Unknown state?"));
            }
            i++;
        }
        if (last < buf.length) {
            int keep = Math.max(this.state, 0);
            ByteBuffer b4 = ByteBufferList.obtain((buf.length - last) - keep).put(buf, last, (buf.length - last) - keep);
            b4.flip();
            ByteBufferList list3 = new ByteBufferList();
            list3.add(b4);
            super.onDataAvailable(this, list3);
        }
    }
}
