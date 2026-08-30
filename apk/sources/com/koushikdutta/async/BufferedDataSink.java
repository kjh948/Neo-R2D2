package com.koushikdutta.async;

import android.support.v7.widget.ActivityChooserView;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;

/* JADX INFO: loaded from: classes.dex */
public class BufferedDataSink implements DataSink {
    static final /* synthetic */ boolean $assertionsDisabled;
    boolean endPending;
    boolean forceBuffering;
    DataSink mDataSink;
    WritableCallback mWritable;
    ByteBufferList mPendingWrites = new ByteBufferList();
    int mMaxBuffer = ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED;

    static {
        $assertionsDisabled = !BufferedDataSink.class.desiredAssertionStatus();
    }

    public BufferedDataSink(DataSink datasink) {
        setDataSink(datasink);
    }

    public boolean isBuffering() {
        return this.mPendingWrites.hasRemaining() || this.forceBuffering;
    }

    public DataSink getDataSink() {
        return this.mDataSink;
    }

    public void forceBuffering(boolean forceBuffering) {
        this.forceBuffering = forceBuffering;
        if (!forceBuffering) {
            writePending();
        }
    }

    public void setDataSink(DataSink datasink) {
        this.mDataSink = datasink;
        this.mDataSink.setWriteableCallback(new WritableCallback() { // from class: com.koushikdutta.async.BufferedDataSink.1
            @Override // com.koushikdutta.async.callback.WritableCallback
            public void onWriteable() {
                BufferedDataSink.this.writePending();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writePending() {
        if (!this.forceBuffering) {
            if (this.mPendingWrites.hasRemaining()) {
                this.mDataSink.write(this.mPendingWrites);
                if (this.mPendingWrites.remaining() == 0 && this.endPending) {
                    this.mDataSink.end();
                }
            }
            if (!this.mPendingWrites.hasRemaining() && this.mWritable != null) {
                this.mWritable.onWriteable();
            }
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void write(ByteBufferList bb) {
        write(bb, false);
    }

    protected void write(final ByteBufferList bb, final boolean ignoreBuffer) {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().run(new Runnable() { // from class: com.koushikdutta.async.BufferedDataSink.2
                @Override // java.lang.Runnable
                public void run() {
                    BufferedDataSink.this.write(bb, ignoreBuffer);
                }
            });
            return;
        }
        if (!isBuffering()) {
            this.mDataSink.write(bb);
        }
        if (bb.remaining() > 0) {
            int toRead = Math.min(bb.remaining(), this.mMaxBuffer);
            if (ignoreBuffer) {
                toRead = bb.remaining();
            }
            if (toRead > 0) {
                bb.get(this.mPendingWrites, toRead);
            }
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void setWriteableCallback(WritableCallback handler) {
        this.mWritable = handler;
    }

    @Override // com.koushikdutta.async.DataSink
    public WritableCallback getWriteableCallback() {
        return this.mWritable;
    }

    public int remaining() {
        return this.mPendingWrites.remaining();
    }

    public int getMaxBuffer() {
        return this.mMaxBuffer;
    }

    public void setMaxBuffer(int maxBuffer) {
        if (!$assertionsDisabled && maxBuffer < 0) {
            throw new AssertionError();
        }
        this.mMaxBuffer = maxBuffer;
    }

    @Override // com.koushikdutta.async.DataSink
    public boolean isOpen() {
        return this.mDataSink.isOpen();
    }

    @Override // com.koushikdutta.async.DataSink
    public void end() {
        if (getServer().getAffinity() != Thread.currentThread()) {
            getServer().run(new Runnable() { // from class: com.koushikdutta.async.BufferedDataSink.3
                @Override // java.lang.Runnable
                public void run() {
                    BufferedDataSink.this.end();
                }
            });
        } else if (this.mPendingWrites.hasRemaining()) {
            this.endPending = true;
        } else {
            this.mDataSink.end();
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void setClosedCallback(CompletedCallback handler) {
        this.mDataSink.setClosedCallback(handler);
    }

    @Override // com.koushikdutta.async.DataSink
    public CompletedCallback getClosedCallback() {
        return this.mDataSink.getClosedCallback();
    }

    @Override // com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mDataSink.getServer();
    }
}
