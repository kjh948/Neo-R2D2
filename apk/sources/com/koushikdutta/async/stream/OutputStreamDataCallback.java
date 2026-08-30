package com.koushikdutta.async.stream;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class OutputStreamDataCallback implements DataCallback, CompletedCallback {
    private OutputStream mOutput;

    public OutputStreamDataCallback(OutputStream os) {
        this.mOutput = os;
    }

    public OutputStream getOutputStream() {
        return this.mOutput;
    }

    @Override // com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        while (bb.size() > 0) {
            try {
                ByteBuffer b = bb.remove();
                this.mOutput.write(b.array(), b.arrayOffset() + b.position(), b.remaining());
                ByteBufferList.reclaim(b);
            } catch (Exception ex) {
                onCompleted(ex);
                return;
            } finally {
                bb.recycle();
            }
        }
    }

    public void close() {
        try {
            this.mOutput.close();
        } catch (IOException e) {
            onCompleted(e);
        }
    }

    @Override // com.koushikdutta.async.callback.CompletedCallback
    public void onCompleted(Exception error) {
        error.printStackTrace();
    }
}
