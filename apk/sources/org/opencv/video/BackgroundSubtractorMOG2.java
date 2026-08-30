package org.opencv.video;

import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class BackgroundSubtractorMOG2 extends BackgroundSubtractor {
    private static native void apply_0(long j, long j2, long j3, double d);

    private static native void apply_1(long j, long j2, long j3);

    private static native void delete(long j);

    private static native double getBackgroundRatio_0(long j);

    private static native double getComplexityReductionThreshold_0(long j);

    private static native boolean getDetectShadows_0(long j);

    private static native int getHistory_0(long j);

    private static native int getNMixtures_0(long j);

    private static native double getShadowThreshold_0(long j);

    private static native int getShadowValue_0(long j);

    private static native double getVarInit_0(long j);

    private static native double getVarMax_0(long j);

    private static native double getVarMin_0(long j);

    private static native double getVarThresholdGen_0(long j);

    private static native double getVarThreshold_0(long j);

    private static native void setBackgroundRatio_0(long j, double d);

    private static native void setComplexityReductionThreshold_0(long j, double d);

    private static native void setDetectShadows_0(long j, boolean z);

    private static native void setHistory_0(long j, int i);

    private static native void setNMixtures_0(long j, int i);

    private static native void setShadowThreshold_0(long j, double d);

    private static native void setShadowValue_0(long j, int i);

    private static native void setVarInit_0(long j, double d);

    private static native void setVarMax_0(long j, double d);

    private static native void setVarMin_0(long j, double d);

    private static native void setVarThresholdGen_0(long j, double d);

    private static native void setVarThreshold_0(long j, double d);

    protected BackgroundSubtractorMOG2(long addr) {
        super(addr);
    }

    public boolean getDetectShadows() {
        boolean retVal = getDetectShadows_0(this.nativeObj);
        return retVal;
    }

    public double getBackgroundRatio() {
        double retVal = getBackgroundRatio_0(this.nativeObj);
        return retVal;
    }

    public double getComplexityReductionThreshold() {
        double retVal = getComplexityReductionThreshold_0(this.nativeObj);
        return retVal;
    }

    public double getShadowThreshold() {
        double retVal = getShadowThreshold_0(this.nativeObj);
        return retVal;
    }

    public double getVarInit() {
        double retVal = getVarInit_0(this.nativeObj);
        return retVal;
    }

    public double getVarMax() {
        double retVal = getVarMax_0(this.nativeObj);
        return retVal;
    }

    public double getVarMin() {
        double retVal = getVarMin_0(this.nativeObj);
        return retVal;
    }

    public double getVarThreshold() {
        double retVal = getVarThreshold_0(this.nativeObj);
        return retVal;
    }

    public double getVarThresholdGen() {
        double retVal = getVarThresholdGen_0(this.nativeObj);
        return retVal;
    }

    public int getHistory() {
        int retVal = getHistory_0(this.nativeObj);
        return retVal;
    }

    public int getNMixtures() {
        int retVal = getNMixtures_0(this.nativeObj);
        return retVal;
    }

    public int getShadowValue() {
        int retVal = getShadowValue_0(this.nativeObj);
        return retVal;
    }

    @Override // org.opencv.video.BackgroundSubtractor
    public void apply(Mat image, Mat fgmask, double learningRate) {
        apply_0(this.nativeObj, image.nativeObj, fgmask.nativeObj, learningRate);
    }

    @Override // org.opencv.video.BackgroundSubtractor
    public void apply(Mat image, Mat fgmask) {
        apply_1(this.nativeObj, image.nativeObj, fgmask.nativeObj);
    }

    public void setBackgroundRatio(double ratio) {
        setBackgroundRatio_0(this.nativeObj, ratio);
    }

    public void setComplexityReductionThreshold(double ct) {
        setComplexityReductionThreshold_0(this.nativeObj, ct);
    }

    public void setDetectShadows(boolean detectShadows) {
        setDetectShadows_0(this.nativeObj, detectShadows);
    }

    public void setHistory(int history) {
        setHistory_0(this.nativeObj, history);
    }

    public void setNMixtures(int nmixtures) {
        setNMixtures_0(this.nativeObj, nmixtures);
    }

    public void setShadowThreshold(double threshold) {
        setShadowThreshold_0(this.nativeObj, threshold);
    }

    public void setShadowValue(int value) {
        setShadowValue_0(this.nativeObj, value);
    }

    public void setVarInit(double varInit) {
        setVarInit_0(this.nativeObj, varInit);
    }

    public void setVarMax(double varMax) {
        setVarMax_0(this.nativeObj, varMax);
    }

    public void setVarMin(double varMin) {
        setVarMin_0(this.nativeObj, varMin);
    }

    public void setVarThreshold(double varThreshold) {
        setVarThreshold_0(this.nativeObj, varThreshold);
    }

    public void setVarThresholdGen(double varThresholdGen) {
        setVarThresholdGen_0(this.nativeObj, varThresholdGen);
    }

    @Override // org.opencv.video.BackgroundSubtractor, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
