package com.koushikdutta.async;

import android.support.v7.widget.ActivityChooserView;
import com.koushikdutta.async.callback.CompletedCallback;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/* JADX INFO: loaded from: classes.dex */
public class ZipDataSink extends FilteredDataSink {
    ByteArrayOutputStream bout;
    ZipOutputStream zop;

    public ZipDataSink(DataSink sink) {
        super(sink);
        this.bout = new ByteArrayOutputStream();
        this.zop = new ZipOutputStream(this.bout);
    }

    public void putNextEntry(ZipEntry ze) throws IOException {
        this.zop.putNextEntry(ze);
    }

    public void closeEntry() throws IOException {
        this.zop.closeEntry();
    }

    protected void report(Exception e) {
        CompletedCallback closed = getClosedCallback();
        if (closed != null) {
            closed.onCompleted(e);
        }
    }

    @Override // com.koushikdutta.async.BufferedDataSink, com.koushikdutta.async.DataSink
    public void end() {
        try {
            this.zop.close();
            setMaxBuffer(ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
            write(new ByteBufferList());
            super.end();
        } catch (IOException e) {
            report(e);
        }
    }

    @Override // com.koushikdutta.async.FilteredDataSink
    public ByteBufferList filter(ByteBufferList bb) {
        ByteBufferList byteBufferList;
        if (bb != null) {
            while (bb.size() > 0) {
                try {
                    try {
                        ByteBuffer b = bb.remove();
                        ByteBufferList.writeOutputStream(this.zop, b);
                        ByteBufferList.reclaim(b);
                    } catch (IOException e) {
                        report(e);
                        byteBufferList = null;
                        if (bb != null) {
                            bb.recycle();
                        }
                    }
                } catch (Throwable th) {
                    if (bb != null) {
                        bb.recycle();
                    }
                    throw th;
                }
            }
        }
        byteBufferList = new ByteBufferList(this.bout.toByteArray());
        this.bout.reset();
        if (bb != null) {
            bb.recycle();
        }
        return byteBufferList;
    }
}
