package com.koushikdutta.async;

import com.koushikdutta.async.DataTrackingEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.wrapper.DataEmitterWrapper;

/* JADX INFO: loaded from: classes.dex */
public class FilteredDataEmitter extends DataEmitterBase implements DataEmitter, DataCallback, DataEmitterWrapper, DataTrackingEmitter {
    boolean closed;
    private DataEmitter mEmitter;
    private int totalRead;
    private DataTrackingEmitter.DataTracker tracker;

    @Override // com.koushikdutta.async.wrapper.DataEmitterWrapper
    public DataEmitter getDataEmitter() {
        return this.mEmitter;
    }

    @Override // com.koushikdutta.async.DataTrackingEmitter
    public void setDataEmitter(DataEmitter emitter) {
        if (this.mEmitter != null) {
            this.mEmitter.setDataCallback(null);
        }
        this.mEmitter = emitter;
        this.mEmitter.setDataCallback(this);
        this.mEmitter.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.FilteredDataEmitter.1
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                FilteredDataEmitter.this.report(ex);
            }
        });
    }

    @Override // com.koushikdutta.async.DataTrackingEmitter
    public int getBytesRead() {
        return this.totalRead;
    }

    @Override // com.koushikdutta.async.DataTrackingEmitter
    public DataTrackingEmitter.DataTracker getDataTracker() {
        return this.tracker;
    }

    @Override // com.koushikdutta.async.DataTrackingEmitter
    public void setDataTracker(DataTrackingEmitter.DataTracker tracker) {
        this.tracker = tracker;
    }

    @Override // com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (this.closed) {
            bb.recycle();
            return;
        }
        if (bb != null) {
            this.totalRead += bb.remaining();
        }
        Util.emitAllData(this, bb);
        if (bb != null) {
            this.totalRead -= bb.remaining();
        }
        if (this.tracker != null && bb != null) {
            this.tracker.onData(this.totalRead);
        }
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isChunked() {
        return this.mEmitter.isChunked();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void pause() {
        this.mEmitter.pause();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void resume() {
        this.mEmitter.resume();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isPaused() {
        return this.mEmitter.isPaused();
    }

    @Override // com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mEmitter.getServer();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void close() {
        this.closed = true;
        if (this.mEmitter != null) {
            this.mEmitter.close();
        }
    }

    @Override // com.koushikdutta.async.DataEmitterBase, com.koushikdutta.async.DataEmitter
    public String charset() {
        if (this.mEmitter == null) {
            return null;
        }
        return this.mEmitter.charset();
    }
}
