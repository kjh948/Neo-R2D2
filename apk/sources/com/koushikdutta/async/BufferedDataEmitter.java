package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;

/* JADX INFO: loaded from: classes.dex */
public class BufferedDataEmitter implements DataEmitter {
    DataCallback mDataCallback;
    DataEmitter mEmitter;
    CompletedCallback mEndCallback;
    Exception mEndException;
    boolean mEnded = false;
    ByteBufferList mBuffers = new ByteBufferList();

    public BufferedDataEmitter(DataEmitter emitter) {
        this.mEmitter = emitter;
        this.mEmitter.setDataCallback(new DataCallback() { // from class: com.koushikdutta.async.BufferedDataEmitter.1
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter2, ByteBufferList bb) {
                bb.get(BufferedDataEmitter.this.mBuffers);
                BufferedDataEmitter.this.onDataAvailable();
            }
        });
        this.mEmitter.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.BufferedDataEmitter.2
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                BufferedDataEmitter.this.mEnded = true;
                BufferedDataEmitter.this.mEndException = ex;
                if (BufferedDataEmitter.this.mBuffers.remaining() == 0 && BufferedDataEmitter.this.mEndCallback != null) {
                    BufferedDataEmitter.this.mEndCallback.onCompleted(ex);
                }
            }
        });
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void close() {
        this.mEmitter.close();
    }

    public void onDataAvailable() {
        if (this.mDataCallback != null && !isPaused() && this.mBuffers.remaining() > 0) {
            this.mDataCallback.onDataAvailable(this, this.mBuffers);
        }
        if (this.mEnded && !this.mBuffers.hasRemaining() && this.mEndCallback != null) {
            this.mEndCallback.onCompleted(this.mEndException);
        }
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void setDataCallback(DataCallback callback) {
        if (this.mDataCallback != null) {
            throw new RuntimeException("Buffered Data Emitter callback may only be set once");
        }
        this.mDataCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public DataCallback getDataCallback() {
        return this.mDataCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isChunked() {
        return false;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void pause() {
        this.mEmitter.pause();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void resume() {
        this.mEmitter.resume();
        onDataAvailable();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isPaused() {
        return this.mEmitter.isPaused();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void setEndCallback(CompletedCallback callback) {
        this.mEndCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public CompletedCallback getEndCallback() {
        return this.mEndCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mEmitter.getServer();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public String charset() {
        return this.mEmitter.charset();
    }
}
