package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;

/* JADX INFO: loaded from: classes.dex */
public class DataEmitterReader implements DataCallback {
    static final /* synthetic */ boolean $assertionsDisabled;
    ByteBufferList mPendingData = new ByteBufferList();
    DataCallback mPendingRead;
    int mPendingReadLength;

    static {
        $assertionsDisabled = !DataEmitterReader.class.desiredAssertionStatus();
    }

    public void read(int count, DataCallback callback) {
        if (!$assertionsDisabled && this.mPendingRead != null) {
            throw new AssertionError();
        }
        this.mPendingReadLength = count;
        this.mPendingRead = callback;
        if (!$assertionsDisabled && this.mPendingData.hasRemaining()) {
            throw new AssertionError();
        }
        this.mPendingData.recycle();
    }

    private boolean handlePendingData(DataEmitter emitter) {
        if (this.mPendingReadLength > this.mPendingData.remaining()) {
            return false;
        }
        DataCallback pendingRead = this.mPendingRead;
        this.mPendingRead = null;
        pendingRead.onDataAvailable(emitter, this.mPendingData);
        if ($assertionsDisabled || !this.mPendingData.hasRemaining()) {
            return true;
        }
        throw new AssertionError();
    }

    @Override // com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        if (!$assertionsDisabled && this.mPendingRead == null) {
            throw new AssertionError();
        }
        do {
            int need = Math.min(bb.remaining(), this.mPendingReadLength - this.mPendingData.remaining());
            bb.get(this.mPendingData, need);
            bb.remaining();
            if (!handlePendingData(emitter)) {
                break;
            }
        } while (this.mPendingRead != null);
        bb.remaining();
    }
}
