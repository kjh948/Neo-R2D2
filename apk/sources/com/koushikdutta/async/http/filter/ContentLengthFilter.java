package com.koushikdutta.async.http.filter;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;

/* JADX INFO: loaded from: classes.dex */
public class ContentLengthFilter extends FilteredDataEmitter {
    static final /* synthetic */ boolean $assertionsDisabled;
    long contentLength;
    long totalRead;
    ByteBufferList transformed = new ByteBufferList();

    static {
        $assertionsDisabled = !ContentLengthFilter.class.desiredAssertionStatus();
    }

    public ContentLengthFilter(long contentLength) {
        this.contentLength = contentLength;
    }

    @Override // com.koushikdutta.async.DataEmitterBase
    protected void report(Exception e) {
        if (e == null && this.totalRead != this.contentLength) {
            e = new PrematureDataEndException("End of data reached before content length was read: " + this.totalRead + "/" + this.contentLength + " Paused: " + isPaused());
        }
        super.report(e);
    }

    @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (!$assertionsDisabled && this.totalRead >= this.contentLength) {
            throw new AssertionError();
        }
        int remaining = bb.remaining();
        long toRead = Math.min(this.contentLength - this.totalRead, remaining);
        bb.get(this.transformed, (int) toRead);
        int beforeRead = this.transformed.remaining();
        super.onDataAvailable(emitter, this.transformed);
        this.totalRead += (long) (beforeRead - this.transformed.remaining());
        this.transformed.get(bb);
        if (this.totalRead == this.contentLength) {
            report(null);
        }
    }
}
