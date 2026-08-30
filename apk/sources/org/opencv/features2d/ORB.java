package org.opencv.features2d;

/* JADX INFO: loaded from: classes.dex */
public class ORB extends Feature2D {
    public static final int FAST_SCORE = 1;
    public static final int HARRIS_SCORE = 0;
    public static final int kBytes = 32;

    private static native long create_0(int i, float f, int i2, int i3, int i4, int i5, int i6, int i7, int i8);

    private static native long create_1();

    private static native void delete(long j);

    private static native int getEdgeThreshold_0(long j);

    private static native int getFastThreshold_0(long j);

    private static native int getFirstLevel_0(long j);

    private static native int getMaxFeatures_0(long j);

    private static native int getNLevels_0(long j);

    private static native int getPatchSize_0(long j);

    private static native double getScaleFactor_0(long j);

    private static native int getScoreType_0(long j);

    private static native int getWTA_K_0(long j);

    private static native void setEdgeThreshold_0(long j, int i);

    private static native void setFastThreshold_0(long j, int i);

    private static native void setFirstLevel_0(long j, int i);

    private static native void setMaxFeatures_0(long j, int i);

    private static native void setNLevels_0(long j, int i);

    private static native void setPatchSize_0(long j, int i);

    private static native void setScaleFactor_0(long j, double d);

    private static native void setScoreType_0(long j, int i);

    private static native void setWTA_K_0(long j, int i);

    protected ORB(long addr) {
        super(addr);
    }

    public static ORB create(int nfeatures, float scaleFactor, int nlevels, int edgeThreshold, int firstLevel, int WTA_K, int scoreType, int patchSize, int fastThreshold) {
        ORB retVal = new ORB(create_0(nfeatures, scaleFactor, nlevels, edgeThreshold, firstLevel, WTA_K, scoreType, patchSize, fastThreshold));
        return retVal;
    }

    public static ORB create() {
        ORB retVal = new ORB(create_1());
        return retVal;
    }

    public double getScaleFactor() {
        double retVal = getScaleFactor_0(this.nativeObj);
        return retVal;
    }

    public int getEdgeThreshold() {
        int retVal = getEdgeThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getFastThreshold() {
        int retVal = getFastThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getFirstLevel() {
        int retVal = getFirstLevel_0(this.nativeObj);
        return retVal;
    }

    public int getMaxFeatures() {
        int retVal = getMaxFeatures_0(this.nativeObj);
        return retVal;
    }

    public int getNLevels() {
        int retVal = getNLevels_0(this.nativeObj);
        return retVal;
    }

    public int getPatchSize() {
        int retVal = getPatchSize_0(this.nativeObj);
        return retVal;
    }

    public int getScoreType() {
        int retVal = getScoreType_0(this.nativeObj);
        return retVal;
    }

    public int getWTA_K() {
        int retVal = getWTA_K_0(this.nativeObj);
        return retVal;
    }

    public void setEdgeThreshold(int edgeThreshold) {
        setEdgeThreshold_0(this.nativeObj, edgeThreshold);
    }

    public void setFastThreshold(int fastThreshold) {
        setFastThreshold_0(this.nativeObj, fastThreshold);
    }

    public void setFirstLevel(int firstLevel) {
        setFirstLevel_0(this.nativeObj, firstLevel);
    }

    public void setMaxFeatures(int maxFeatures) {
        setMaxFeatures_0(this.nativeObj, maxFeatures);
    }

    public void setNLevels(int nlevels) {
        setNLevels_0(this.nativeObj, nlevels);
    }

    public void setPatchSize(int patchSize) {
        setPatchSize_0(this.nativeObj, patchSize);
    }

    public void setScaleFactor(double scaleFactor) {
        setScaleFactor_0(this.nativeObj, scaleFactor);
    }

    public void setScoreType(int scoreType) {
        setScoreType_0(this.nativeObj, scoreType);
    }

    public void setWTA_K(int wta_k) {
        setWTA_K_0(this.nativeObj, wta_k);
    }

    @Override // org.opencv.features2d.Feature2D, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
