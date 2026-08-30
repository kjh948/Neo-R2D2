package com.koushikdutta.async.http.filter;

import android.support.v7.widget.ActivityChooserView;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.FilteredDataSink;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class ChunkedOutputFilter extends FilteredDataSink {
    public ChunkedOutputFilter(DataSink sink) {
        super(sink);
    }

    @Override // com.koushikdutta.async.FilteredDataSink
    public ByteBufferList filter(ByteBufferList bb) {
        String chunkLen = Integer.toString(bb.remaining(), 16) + "\r\n";
        bb.addFirst(ByteBuffer.wrap(chunkLen.getBytes()));
        bb.add(ByteBuffer.wrap("\r\n".getBytes()));
        return bb;
    }

    @Override // com.koushikdutta.async.BufferedDataSink, com.koushikdutta.async.DataSink
    public void end() {
        setMaxBuffer(ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
        ByteBufferList fin = new ByteBufferList();
        write(fin);
        setMaxBuffer(0);
    }
}
