package org.opencv.features2d;

/* JADX INFO: loaded from: classes.dex */
public class AgastFeatureDetector extends Feature2D {
    public static final int AGAST_5_8 = 0;
    public static final int AGAST_7_12d = 1;
    public static final int AGAST_7_12s = 2;
    public static final int NONMAX_SUPPRESSION = 10001;
    public static final int OAST_9_16 = 3;
    public static final int THRESHOLD = 10000;

    private static native long create_0(int i, boolean z, int i2);

    private static native long create_1();

    private static native void delete(long j);

    private static native boolean getNonmaxSuppression_0(long j);

    private static native int getThreshold_0(long j);

    private static native int getType_0(long j);

    private static native void setNonmaxSuppression_0(long j, boolean z);

    private static native void setThreshold_0(long j, int i);

    private static native void setType_0(long j, int i);

    protected AgastFeatureDetector(long addr) {
        super(addr);
    }

    public static AgastFeatureDetector create(int threshold, boolean nonmaxSuppression, int type) {
        AgastFeatureDetector retVal = new AgastFeatureDetector(create_0(threshold, nonmaxSuppression, type));
        return retVal;
    }

    public static AgastFeatureDetector create() {
        AgastFeatureDetector retVal = new AgastFeatureDetector(create_1());
        return retVal;
    }

    public boolean getNonmaxSuppression() {
        boolean retVal = getNonmaxSuppression_0(this.nativeObj);
        return retVal;
    }

    public int getThreshold() {
        int retVal = getThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getType() {
        int retVal = getType_0(this.nativeObj);
        return retVal;
    }

    public void setNonmaxSuppression(boolean f) {
        setNonmaxSuppression_0(this.nativeObj, f);
    }

    public void setThreshold(int threshold) {
        setThreshold_0(this.nativeObj, threshold);
    }

    public void setType(int type) {
        setType_0(this.nativeObj, type);
    }

    @Override // org.opencv.features2d.Feature2D, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
