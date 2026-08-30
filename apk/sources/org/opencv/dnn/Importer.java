package org.opencv.dnn;

import org.opencv.core.Algorithm;

/* JADX INFO: loaded from: classes.dex */
public class Importer extends Algorithm {
    private static native void delete(long j);

    private static native void populateNet_0(long j, long j2);

    protected Importer(long addr) {
        super(addr);
    }

    public void populateNet(Net net) {
        populateNet_0(this.nativeObj, net.nativeObj);
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
