package org.opencv.photo;

import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class CalibrateRobertson extends CalibrateCRF {
    private static native void delete(long j);

    private static native int getMaxIter_0(long j);

    private static native long getRadiance_0(long j);

    private static native float getThreshold_0(long j);

    private static native void setMaxIter_0(long j, int i);

    private static native void setThreshold_0(long j, float f);

    protected CalibrateRobertson(long addr) {
        super(addr);
    }

    public Mat getRadiance() {
        Mat retVal = new Mat(getRadiance_0(this.nativeObj));
        return retVal;
    }

    public float getThreshold() {
        float retVal = getThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getMaxIter() {
        int retVal = getMaxIter_0(this.nativeObj);
        return retVal;
    }

    public void setMaxIter(int max_iter) {
        setMaxIter_0(this.nativeObj, max_iter);
    }

    public void setThreshold(float threshold) {
        setThreshold_0(this.nativeObj, threshold);
    }

    @Override // org.opencv.photo.CalibrateCRF, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
