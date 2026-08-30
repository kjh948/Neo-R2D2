package com.koushikdutta.async;

/* JADX INFO: loaded from: classes.dex */
public class FilteredDataSink extends BufferedDataSink {
    static final /* synthetic */ boolean $assertionsDisabled;

    static {
        $assertionsDisabled = !FilteredDataSink.class.desiredAssertionStatus();
    }

    public FilteredDataSink(DataSink sink) {
        super(sink);
        setMaxBuffer(0);
    }

    public ByteBufferList filter(ByteBufferList bb) {
        return bb;
    }

    @Override // com.koushikdutta.async.BufferedDataSink, com.koushikdutta.async.DataSink
    public final void write(ByteBufferList bb) {
        if (!isBuffering() || getMaxBuffer() == Integer.MAX_VALUE) {
            ByteBufferList filtered = filter(bb);
            if (!$assertionsDisabled && bb != null && filtered != bb && !bb.isEmpty()) {
                throw new AssertionError();
            }
            super.write(filtered, true);
            if (bb != null) {
                bb.recycle();
            }
        }
    }
}
