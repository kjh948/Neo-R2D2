package org.opencv.features2d;

/* JADX INFO: loaded from: classes.dex */
public class GFTTDetector extends Feature2D {
    private static native long create_0(int i, double d, double d2, int i2, boolean z, double d3);

    private static native long create_1();

    private static native void delete(long j);

    private static native int getBlockSize_0(long j);

    private static native boolean getHarrisDetector_0(long j);

    private static native double getK_0(long j);

    private static native int getMaxFeatures_0(long j);

    private static native double getMinDistance_0(long j);

    private static native double getQualityLevel_0(long j);

    private static native void setBlockSize_0(long j, int i);

    private static native void setHarrisDetector_0(long j, boolean z);

    private static native void setK_0(long j, double d);

    private static native void setMaxFeatures_0(long j, int i);

    private static native void setMinDistance_0(long j, double d);

    private static native void setQualityLevel_0(long j, double d);

    protected GFTTDetector(long addr) {
        super(addr);
    }

    public static GFTTDetector create(int maxCorners, double qualityLevel, double minDistance, int blockSize, boolean useHarrisDetector, double k) {
        GFTTDetector retVal = new GFTTDetector(create_0(maxCorners, qualityLevel, minDistance, blockSize, useHarrisDetector, k));
        return retVal;
    }

    public static GFTTDetector create() {
        GFTTDetector retVal = new GFTTDetector(create_1());
        return retVal;
    }

    public boolean getHarrisDetector() {
        boolean retVal = getHarrisDetector_0(this.nativeObj);
        return retVal;
    }

    public double getK() {
        double retVal = getK_0(this.nativeObj);
        return retVal;
    }

    public double getMinDistance() {
        double retVal = getMinDistance_0(this.nativeObj);
        return retVal;
    }

    public double getQualityLevel() {
        double retVal = getQualityLevel_0(this.nativeObj);
        return retVal;
    }

    public int getBlockSize() {
        int retVal = getBlockSize_0(this.nativeObj);
        return retVal;
    }

    public int getMaxFeatures() {
        int retVal = getMaxFeatures_0(this.nativeObj);
        return retVal;
    }

    public void setBlockSize(int blockSize) {
        setBlockSize_0(this.nativeObj, blockSize);
    }

    public void setHarrisDetector(boolean val) {
        setHarrisDetector_0(this.nativeObj, val);
    }

    public void setK(double k) {
        setK_0(this.nativeObj, k);
    }

    public void setMaxFeatures(int maxFeatures) {
        setMaxFeatures_0(this.nativeObj, maxFeatures);
    }

    public void setMinDistance(double minDistance) {
        setMinDistance_0(this.nativeObj, minDistance);
    }

    public void setQualityLevel(double qlevel) {
        setQualityLevel_0(this.nativeObj, qlevel);
    }

    @Override // org.opencv.features2d.Feature2D, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
