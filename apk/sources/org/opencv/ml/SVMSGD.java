package org.opencv.ml;

import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

/* JADX INFO: loaded from: classes.dex */
public class SVMSGD extends StatModel {
    public static final int ASGD = 1;
    public static final int HARD_MARGIN = 1;
    public static final int SGD = 0;
    public static final int SOFT_MARGIN = 0;

    private static native long create_0();

    private static native void delete(long j);

    private static native float getInitialStepSize_0(long j);

    private static native float getMarginRegularization_0(long j);

    private static native int getMarginType_0(long j);

    private static native float getShift_0(long j);

    private static native float getStepDecreasingPower_0(long j);

    private static native int getSvmsgdType_0(long j);

    private static native double[] getTermCriteria_0(long j);

    private static native long getWeights_0(long j);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native void setInitialStepSize_0(long j, float f);

    private static native void setMarginRegularization_0(long j, float f);

    private static native void setMarginType_0(long j, int i);

    private static native void setOptimalParameters_0(long j, int i, int i2);

    private static native void setOptimalParameters_1(long j);

    private static native void setStepDecreasingPower_0(long j, float f);

    private static native void setSvmsgdType_0(long j, int i);

    private static native void setTermCriteria_0(long j, int i, int i2, double d);

    protected SVMSGD(long addr) {
        super(addr);
    }

    public Mat getWeights() {
        Mat retVal = new Mat(getWeights_0(this.nativeObj));
        return retVal;
    }

    public static SVMSGD create() {
        SVMSGD retVal = new SVMSGD(create_0());
        return retVal;
    }

    public static SVMSGD load(String filepath, String nodeName) {
        SVMSGD retVal = new SVMSGD(load_0(filepath, nodeName));
        return retVal;
    }

    public static SVMSGD load(String filepath) {
        SVMSGD retVal = new SVMSGD(load_1(filepath));
        return retVal;
    }

    public TermCriteria getTermCriteria() {
        TermCriteria retVal = new TermCriteria(getTermCriteria_0(this.nativeObj));
        return retVal;
    }

    public float getInitialStepSize() {
        float retVal = getInitialStepSize_0(this.nativeObj);
        return retVal;
    }

    public float getMarginRegularization() {
        float retVal = getMarginRegularization_0(this.nativeObj);
        return retVal;
    }

    public float getShift() {
        float retVal = getShift_0(this.nativeObj);
        return retVal;
    }

    public float getStepDecreasingPower() {
        float retVal = getStepDecreasingPower_0(this.nativeObj);
        return retVal;
    }

    public int getMarginType() {
        int retVal = getMarginType_0(this.nativeObj);
        return retVal;
    }

    public int getSvmsgdType() {
        int retVal = getSvmsgdType_0(this.nativeObj);
        return retVal;
    }

    public void setInitialStepSize(float InitialStepSize) {
        setInitialStepSize_0(this.nativeObj, InitialStepSize);
    }

    public void setMarginRegularization(float marginRegularization) {
        setMarginRegularization_0(this.nativeObj, marginRegularization);
    }

    public void setMarginType(int marginType) {
        setMarginType_0(this.nativeObj, marginType);
    }

    public void setOptimalParameters(int svmsgdType, int marginType) {
        setOptimalParameters_0(this.nativeObj, svmsgdType, marginType);
    }

    public void setOptimalParameters() {
        setOptimalParameters_1(this.nativeObj);
    }

    public void setStepDecreasingPower(float stepDecreasingPower) {
        setStepDecreasingPower_0(this.nativeObj, stepDecreasingPower);
    }

    public void setSvmsgdType(int svmsgdType) {
        setSvmsgdType_0(this.nativeObj, svmsgdType);
    }

    public void setTermCriteria(TermCriteria val) {
        setTermCriteria_0(this.nativeObj, val.type, val.maxCount, val.epsilon);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
