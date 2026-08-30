package org.opencv.photo;

/* JADX INFO: loaded from: classes.dex */
public class CalibrateDebevec extends CalibrateCRF {
    private static native void delete(long j);

    private static native float getLambda_0(long j);

    private static native boolean getRandom_0(long j);

    private static native int getSamples_0(long j);

    private static native void setLambda_0(long j, float f);

    private static native void setRandom_0(long j, boolean z);

    private static native void setSamples_0(long j, int i);

    protected CalibrateDebevec(long addr) {
        super(addr);
    }

    public boolean getRandom() {
        boolean retVal = getRandom_0(this.nativeObj);
        return retVal;
    }

    public float getLambda() {
        float retVal = getLambda_0(this.nativeObj);
        return retVal;
    }

    public int getSamples() {
        int retVal = getSamples_0(this.nativeObj);
        return retVal;
    }

    public void setLambda(float lambda) {
        setLambda_0(this.nativeObj, lambda);
    }

    public void setRandom(boolean random) {
        setRandom_0(this.nativeObj, random);
    }

    public void setSamples(int samples) {
        setSamples_0(this.nativeObj, samples);
    }

    @Override // org.opencv.photo.CalibrateCRF, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
