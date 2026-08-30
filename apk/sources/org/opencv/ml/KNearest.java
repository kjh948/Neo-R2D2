package org.opencv.ml;

import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class KNearest extends StatModel {
    public static final int BRUTE_FORCE = 1;
    public static final int KDTREE = 2;

    private static native long create_0();

    private static native void delete(long j);

    private static native float findNearest_0(long j, long j2, int i, long j3, long j4, long j5);

    private static native float findNearest_1(long j, long j2, int i, long j3);

    private static native int getAlgorithmType_0(long j);

    private static native int getDefaultK_0(long j);

    private static native int getEmax_0(long j);

    private static native boolean getIsClassifier_0(long j);

    private static native void setAlgorithmType_0(long j, int i);

    private static native void setDefaultK_0(long j, int i);

    private static native void setEmax_0(long j, int i);

    private static native void setIsClassifier_0(long j, boolean z);

    protected KNearest(long addr) {
        super(addr);
    }

    public static KNearest create() {
        KNearest retVal = new KNearest(create_0());
        return retVal;
    }

    public boolean getIsClassifier() {
        boolean retVal = getIsClassifier_0(this.nativeObj);
        return retVal;
    }

    public float findNearest(Mat samples, int k, Mat results, Mat neighborResponses, Mat dist) {
        float retVal = findNearest_0(this.nativeObj, samples.nativeObj, k, results.nativeObj, neighborResponses.nativeObj, dist.nativeObj);
        return retVal;
    }

    public float findNearest(Mat samples, int k, Mat results) {
        float retVal = findNearest_1(this.nativeObj, samples.nativeObj, k, results.nativeObj);
        return retVal;
    }

    public int getAlgorithmType() {
        int retVal = getAlgorithmType_0(this.nativeObj);
        return retVal;
    }

    public int getDefaultK() {
        int retVal = getDefaultK_0(this.nativeObj);
        return retVal;
    }

    public int getEmax() {
        int retVal = getEmax_0(this.nativeObj);
        return retVal;
    }

    public void setAlgorithmType(int val) {
        setAlgorithmType_0(this.nativeObj, val);
    }

    public void setDefaultK(int val) {
        setDefaultK_0(this.nativeObj, val);
    }

    public void setEmax(int val) {
        setEmax_0(this.nativeObj, val);
    }

    public void setIsClassifier(boolean val) {
        setIsClassifier_0(this.nativeObj, val);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
