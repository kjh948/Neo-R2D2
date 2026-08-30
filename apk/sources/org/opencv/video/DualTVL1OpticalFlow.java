package org.opencv.video;

/* JADX INFO: loaded from: classes.dex */
public class DualTVL1OpticalFlow extends DenseOpticalFlow {
    private static native long create_0(double d, double d2, double d3, int i, int i2, double d4, int i3, int i4, double d5, double d6, int i5, boolean z);

    private static native long create_1();

    private static native void delete(long j);

    private static native double getEpsilon_0(long j);

    private static native double getGamma_0(long j);

    private static native int getInnerIterations_0(long j);

    private static native double getLambda_0(long j);

    private static native int getMedianFiltering_0(long j);

    private static native int getOuterIterations_0(long j);

    private static native double getScaleStep_0(long j);

    private static native int getScalesNumber_0(long j);

    private static native double getTau_0(long j);

    private static native double getTheta_0(long j);

    private static native boolean getUseInitialFlow_0(long j);

    private static native int getWarpingsNumber_0(long j);

    private static native void setEpsilon_0(long j, double d);

    private static native void setGamma_0(long j, double d);

    private static native void setInnerIterations_0(long j, int i);

    private static native void setLambda_0(long j, double d);

    private static native void setMedianFiltering_0(long j, int i);

    private static native void setOuterIterations_0(long j, int i);

    private static native void setScaleStep_0(long j, double d);

    private static native void setScalesNumber_0(long j, int i);

    private static native void setTau_0(long j, double d);

    private static native void setTheta_0(long j, double d);

    private static native void setUseInitialFlow_0(long j, boolean z);

    private static native void setWarpingsNumber_0(long j, int i);

    protected DualTVL1OpticalFlow(long addr) {
        super(addr);
    }

    public static DualTVL1OpticalFlow create(double tau, double lambda, double theta, int nscales, int warps, double epsilon, int innnerIterations, int outerIterations, double scaleStep, double gamma, int medianFiltering, boolean useInitialFlow) {
        DualTVL1OpticalFlow retVal = new DualTVL1OpticalFlow(create_0(tau, lambda, theta, nscales, warps, epsilon, innnerIterations, outerIterations, scaleStep, gamma, medianFiltering, useInitialFlow));
        return retVal;
    }

    public static DualTVL1OpticalFlow create() {
        DualTVL1OpticalFlow retVal = new DualTVL1OpticalFlow(create_1());
        return retVal;
    }

    public boolean getUseInitialFlow() {
        boolean retVal = getUseInitialFlow_0(this.nativeObj);
        return retVal;
    }

    public double getEpsilon() {
        double retVal = getEpsilon_0(this.nativeObj);
        return retVal;
    }

    public double getGamma() {
        double retVal = getGamma_0(this.nativeObj);
        return retVal;
    }

    public double getLambda() {
        double retVal = getLambda_0(this.nativeObj);
        return retVal;
    }

    public double getScaleStep() {
        double retVal = getScaleStep_0(this.nativeObj);
        return retVal;
    }

    public double getTau() {
        double retVal = getTau_0(this.nativeObj);
        return retVal;
    }

    public double getTheta() {
        double retVal = getTheta_0(this.nativeObj);
        return retVal;
    }

    public int getInnerIterations() {
        int retVal = getInnerIterations_0(this.nativeObj);
        return retVal;
    }

    public int getMedianFiltering() {
        int retVal = getMedianFiltering_0(this.nativeObj);
        return retVal;
    }

    public int getOuterIterations() {
        int retVal = getOuterIterations_0(this.nativeObj);
        return retVal;
    }

    public int getScalesNumber() {
        int retVal = getScalesNumber_0(this.nativeObj);
        return retVal;
    }

    public int getWarpingsNumber() {
        int retVal = getWarpingsNumber_0(this.nativeObj);
        return retVal;
    }

    public void setEpsilon(double val) {
        setEpsilon_0(this.nativeObj, val);
    }

    public void setGamma(double val) {
        setGamma_0(this.nativeObj, val);
    }

    public void setInnerIterations(int val) {
        setInnerIterations_0(this.nativeObj, val);
    }

    public void setLambda(double val) {
        setLambda_0(this.nativeObj, val);
    }

    public void setMedianFiltering(int val) {
        setMedianFiltering_0(this.nativeObj, val);
    }

    public void setOuterIterations(int val) {
        setOuterIterations_0(this.nativeObj, val);
    }

    public void setScaleStep(double val) {
        setScaleStep_0(this.nativeObj, val);
    }

    public void setScalesNumber(int val) {
        setScalesNumber_0(this.nativeObj, val);
    }

    public void setTau(double val) {
        setTau_0(this.nativeObj, val);
    }

    public void setTheta(double val) {
        setTheta_0(this.nativeObj, val);
    }

    public void setUseInitialFlow(boolean val) {
        setUseInitialFlow_0(this.nativeObj, val);
    }

    public void setWarpingsNumber(int val) {
        setWarpingsNumber_0(this.nativeObj, val);
    }

    @Override // org.opencv.video.DenseOpticalFlow, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
