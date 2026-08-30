package org.opencv.video;

/* JADX INFO: loaded from: classes.dex */
public class BackgroundSubtractorKNN extends BackgroundSubtractor {
    private static native void delete(long j);

    private static native boolean getDetectShadows_0(long j);

    private static native double getDist2Threshold_0(long j);

    private static native int getHistory_0(long j);

    private static native int getNSamples_0(long j);

    private static native double getShadowThreshold_0(long j);

    private static native int getShadowValue_0(long j);

    private static native int getkNNSamples_0(long j);

    private static native void setDetectShadows_0(long j, boolean z);

    private static native void setDist2Threshold_0(long j, double d);

    private static native void setHistory_0(long j, int i);

    private static native void setNSamples_0(long j, int i);

    private static native void setShadowThreshold_0(long j, double d);

    private static native void setShadowValue_0(long j, int i);

    private static native void setkNNSamples_0(long j, int i);

    protected BackgroundSubtractorKNN(long addr) {
        super(addr);
    }

    public boolean getDetectShadows() {
        boolean retVal = getDetectShadows_0(this.nativeObj);
        return retVal;
    }

    public double getDist2Threshold() {
        double retVal = getDist2Threshold_0(this.nativeObj);
        return retVal;
    }

    public double getShadowThreshold() {
        double retVal = getShadowThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getHistory() {
        int retVal = getHistory_0(this.nativeObj);
        return retVal;
    }

    public int getNSamples() {
        int retVal = getNSamples_0(this.nativeObj);
        return retVal;
    }

    public int getShadowValue() {
        int retVal = getShadowValue_0(this.nativeObj);
        return retVal;
    }

    public int getkNNSamples() {
        int retVal = getkNNSamples_0(this.nativeObj);
        return retVal;
    }

    public void setDetectShadows(boolean detectShadows) {
        setDetectShadows_0(this.nativeObj, detectShadows);
    }

    public void setDist2Threshold(double _dist2Threshold) {
        setDist2Threshold_0(this.nativeObj, _dist2Threshold);
    }

    public void setHistory(int history) {
        setHistory_0(this.nativeObj, history);
    }

    public void setNSamples(int _nN) {
        setNSamples_0(this.nativeObj, _nN);
    }

    public void setShadowThreshold(double threshold) {
        setShadowThreshold_0(this.nativeObj, threshold);
    }

    public void setShadowValue(int value) {
        setShadowValue_0(this.nativeObj, value);
    }

    public void setkNNSamples(int _nkNN) {
        setkNNSamples_0(this.nativeObj, _nkNN);
    }

    @Override // org.opencv.video.BackgroundSubtractor, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
