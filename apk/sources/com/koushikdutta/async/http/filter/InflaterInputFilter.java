package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.Util;
import java.nio.ByteBuffer;
import java.util.zip.Inflater;

/* JADX INFO: loaded from: classes.dex */
public class InflaterInputFilter extends FilteredDataEmitter {
    static final /* synthetic */ boolean $assertionsDisabled;
    private Inflater mInflater;
    ByteBufferList transformed;

    static {
        $assertionsDisabled = !InflaterInputFilter.class.desiredAssertionStatus();
    }

    @Override // com.koushikdutta.async.DataEmitterBase
    protected void report(Exception e) {
        this.mInflater.end();
        if (e != null && this.mInflater.getRemaining() > 0) {
            e = new DataRemainingException("data still remaining in inflater", e);
        }
        super.report(e);
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        try {
            ByteBuffer output = ByteBufferList.obtain(bb.remaining() * 2);
            while (bb.size() > 0) {
                ByteBuffer b = bb.remove();
                if (b.hasRemaining()) {
                    int totalRead = b.remaining();
                    this.mInflater.setInput(b.array(), b.arrayOffset() + b.position(), b.remaining());
                    do {
                        int inflated = this.mInflater.inflate(output.array(), output.arrayOffset() + output.position(), output.remaining());
                        output.position(output.position() + inflated);
                        if (!output.hasRemaining()) {
                            output.flip();
                            this.transformed.add(output);
                            if (!$assertionsDisabled && totalRead == 0) {
                                throw new AssertionError();
                            }
                            int newSize = output.capacity() * 2;
                            output = ByteBufferList.obtain(newSize);
                        }
                        if (!this.mInflater.needsInput()) {
                        }
                    } while (!this.mInflater.finished());
                }
                ByteBufferList.reclaim(b);
            }
            output.flip();
            this.transformed.add(output);
            Util.emitAllData(this, this.transformed);
        } catch (Exception ex) {
            report(ex);
        }
    }

    public InflaterInputFilter() {
        this(new Inflater());
    }

    public InflaterInputFilter(Inflater inflater) {
        this.transformed = new ByteBufferList();
        this.mInflater = inflater;
    }
}
