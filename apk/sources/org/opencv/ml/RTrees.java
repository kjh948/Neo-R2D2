package org.opencv.ml;

import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

/* JADX INFO: loaded from: classes.dex */
public class RTrees extends DTrees {
    private static native long create_0();

    private static native void delete(long j);

    private static native int getActiveVarCount_0(long j);

    private static native boolean getCalculateVarImportance_0(long j);

    private static native double[] getTermCriteria_0(long j);

    private static native long getVarImportance_0(long j);

    private static native void getVotes_0(long j, long j2, long j3, int i);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native void setActiveVarCount_0(long j, int i);

    private static native void setCalculateVarImportance_0(long j, boolean z);

    private static native void setTermCriteria_0(long j, int i, int i2, double d);

    protected RTrees(long addr) {
        super(addr);
    }

    public Mat getVarImportance() {
        Mat retVal = new Mat(getVarImportance_0(this.nativeObj));
        return retVal;
    }

    public static RTrees create() {
        RTrees retVal = new RTrees(create_0());
        return retVal;
    }

    public static RTrees load(String filepath, String nodeName) {
        RTrees retVal = new RTrees(load_0(filepath, nodeName));
        return retVal;
    }

    public static RTrees load(String filepath) {
        RTrees retVal = new RTrees(load_1(filepath));
        return retVal;
    }

    public TermCriteria getTermCriteria() {
        TermCriteria retVal = new TermCriteria(getTermCriteria_0(this.nativeObj));
        return retVal;
    }

    public boolean getCalculateVarImportance() {
        boolean retVal = getCalculateVarImportance_0(this.nativeObj);
        return retVal;
    }

    public int getActiveVarCount() {
        int retVal = getActiveVarCount_0(this.nativeObj);
        return retVal;
    }

    public void getVotes(Mat samples, Mat results, int flags) {
        getVotes_0(this.nativeObj, samples.nativeObj, results.nativeObj, flags);
    }

    public void setActiveVarCount(int val) {
        setActiveVarCount_0(this.nativeObj, val);
    }

    public void setCalculateVarImportance(boolean val) {
        setCalculateVarImportance_0(this.nativeObj, val);
    }

    public void setTermCriteria(TermCriteria val) {
        setTermCriteria_0(this.nativeObj, val.type, val.maxCount, val.epsilon);
    }

    @Override // org.opencv.ml.DTrees, org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
