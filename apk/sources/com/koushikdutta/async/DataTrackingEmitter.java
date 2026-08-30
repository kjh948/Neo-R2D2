package com.koushikdutta.async;

/* JADX INFO: loaded from: classes.dex */
public interface DataTrackingEmitter extends DataEmitter {

    public interface DataTracker {
        void onData(int i);
    }

    int getBytesRead();

    DataTracker getDataTracker();

    void setDataEmitter(DataEmitter dataEmitter);

    void setDataTracker(DataTracker dataTracker);
}
