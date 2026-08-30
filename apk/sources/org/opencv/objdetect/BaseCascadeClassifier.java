package org.opencv.objdetect;

import org.opencv.core.Algorithm;

/* JADX INFO: loaded from: classes.dex */
public class BaseCascadeClassifier extends Algorithm {
    private static native void delete(long j);

    protected BaseCascadeClassifier(long addr) {
        super(addr);
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
