package org.opencv.ml;

import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class DTrees extends StatModel {
    public static final int PREDICT_AUTO = 0;
    public static final int PREDICT_MASK = 768;
    public static final int PREDICT_MAX_VOTE = 512;
    public static final int PREDICT_SUM = 256;

    private static native long create_0();

    private static native void delete(long j);

    private static native int getCVFolds_0(long j);

    private static native int getMaxCategories_0(long j);

    private static native int getMaxDepth_0(long j);

    private static native int getMinSampleCount_0(long j);

    private static native long getPriors_0(long j);

    private static native float getRegressionAccuracy_0(long j);

    private static native boolean getTruncatePrunedTree_0(long j);

    private static native boolean getUse1SERule_0(long j);

    private static native boolean getUseSurrogates_0(long j);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native void setCVFolds_0(long j, int i);

    private static native void setMaxCategories_0(long j, int i);

    private static native void setMaxDepth_0(long j, int i);

    private static native void setMinSampleCount_0(long j, int i);

    private static native void setPriors_0(long j, long j2);

    private static native void setRegressionAccuracy_0(long j, float f);

    private static native void setTruncatePrunedTree_0(long j, boolean z);

    private static native void setUse1SERule_0(long j, boolean z);

    private static native void setUseSurrogates_0(long j, boolean z);

    protected DTrees(long addr) {
        super(addr);
    }

    public Mat getPriors() {
        Mat retVal = new Mat(getPriors_0(this.nativeObj));
        return retVal;
    }

    public static DTrees create() {
        DTrees retVal = new DTrees(create_0());
        return retVal;
    }

    public static DTrees load(String filepath, String nodeName) {
        DTrees retVal = new DTrees(load_0(filepath, nodeName));
        return retVal;
    }

    public static DTrees load(String filepath) {
        DTrees retVal = new DTrees(load_1(filepath));
        return retVal;
    }

    public boolean getTruncatePrunedTree() {
        boolean retVal = getTruncatePrunedTree_0(this.nativeObj);
        return retVal;
    }

    public boolean getUse1SERule() {
        boolean retVal = getUse1SERule_0(this.nativeObj);
        return retVal;
    }

    public boolean getUseSurrogates() {
        boolean retVal = getUseSurrogates_0(this.nativeObj);
        return retVal;
    }

    public float getRegressionAccuracy() {
        float retVal = getRegressionAccuracy_0(this.nativeObj);
        return retVal;
    }

    public int getCVFolds() {
        int retVal = getCVFolds_0(this.nativeObj);
        return retVal;
    }

    public int getMaxCategories() {
        int retVal = getMaxCategories_0(this.nativeObj);
        return retVal;
    }

    public int getMaxDepth() {
        int retVal = getMaxDepth_0(this.nativeObj);
        return retVal;
    }

    public int getMinSampleCount() {
        int retVal = getMinSampleCount_0(this.nativeObj);
        return retVal;
    }

    public void setCVFolds(int val) {
        setCVFolds_0(this.nativeObj, val);
    }

    public void setMaxCategories(int val) {
        setMaxCategories_0(this.nativeObj, val);
    }

    public void setMaxDepth(int val) {
        setMaxDepth_0(this.nativeObj, val);
    }

    public void setMinSampleCount(int val) {
        setMinSampleCount_0(this.nativeObj, val);
    }

    public void setPriors(Mat val) {
        setPriors_0(this.nativeObj, val.nativeObj);
    }

    public void setRegressionAccuracy(float val) {
        setRegressionAccuracy_0(this.nativeObj, val);
    }

    public void setTruncatePrunedTree(boolean val) {
        setTruncatePrunedTree_0(this.nativeObj, val);
    }

    public void setUse1SERule(boolean val) {
        setUse1SERule_0(this.nativeObj, val);
    }

    public void setUseSurrogates(boolean val) {
        setUseSurrogates_0(this.nativeObj, val);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
