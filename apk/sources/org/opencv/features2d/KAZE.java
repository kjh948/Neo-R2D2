package org.opencv.features2d;

/* JADX INFO: loaded from: classes.dex */
public class KAZE extends Feature2D {
    public static final int DIFF_CHARBONNIER = 3;
    public static final int DIFF_PM_G1 = 0;
    public static final int DIFF_PM_G2 = 1;
    public static final int DIFF_WEICKERT = 2;

    private static native long create_0(boolean z, boolean z2, float f, int i, int i2, int i3);

    private static native long create_1();

    private static native void delete(long j);

    private static native int getDiffusivity_0(long j);

    private static native boolean getExtended_0(long j);

    private static native int getNOctaveLayers_0(long j);

    private static native int getNOctaves_0(long j);

    private static native double getThreshold_0(long j);

    private static native boolean getUpright_0(long j);

    private static native void setDiffusivity_0(long j, int i);

    private static native void setExtended_0(long j, boolean z);

    private static native void setNOctaveLayers_0(long j, int i);

    private static native void setNOctaves_0(long j, int i);

    private static native void setThreshold_0(long j, double d);

    private static native void setUpright_0(long j, boolean z);

    protected KAZE(long addr) {
        super(addr);
    }

    public static KAZE create(boolean extended, boolean upright, float threshold, int nOctaves, int nOctaveLayers, int diffusivity) {
        KAZE retVal = new KAZE(create_0(extended, upright, threshold, nOctaves, nOctaveLayers, diffusivity));
        return retVal;
    }

    public static KAZE create() {
        KAZE retVal = new KAZE(create_1());
        return retVal;
    }

    public boolean getExtended() {
        boolean retVal = getExtended_0(this.nativeObj);
        return retVal;
    }

    public boolean getUpright() {
        boolean retVal = getUpright_0(this.nativeObj);
        return retVal;
    }

    public double getThreshold() {
        double retVal = getThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getDiffusivity() {
        int retVal = getDiffusivity_0(this.nativeObj);
        return retVal;
    }

    public int getNOctaveLayers() {
        int retVal = getNOctaveLayers_0(this.nativeObj);
        return retVal;
    }

    public int getNOctaves() {
        int retVal = getNOctaves_0(this.nativeObj);
        return retVal;
    }

    public void setDiffusivity(int diff) {
        setDiffusivity_0(this.nativeObj, diff);
    }

    public void setExtended(boolean extended) {
        setExtended_0(this.nativeObj, extended);
    }

    public void setNOctaveLayers(int octaveLayers) {
        setNOctaveLayers_0(this.nativeObj, octaveLayers);
    }

    public void setNOctaves(int octaves) {
        setNOctaves_0(this.nativeObj, octaves);
    }

    public void setThreshold(double threshold) {
        setThreshold_0(this.nativeObj, threshold);
    }

    public void setUpright(boolean upright) {
        setUpright_0(this.nativeObj, upright);
    }

    @Override // org.opencv.features2d.Feature2D, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
