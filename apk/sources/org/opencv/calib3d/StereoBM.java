package org.opencv.calib3d;

import org.opencv.core.Rect;

/* JADX INFO: loaded from: classes.dex */
public class StereoBM extends StereoMatcher {
    public static final int PREFILTER_NORMALIZED_RESPONSE = 0;
    public static final int PREFILTER_XSOBEL = 1;

    private static native long create_0(int i, int i2);

    private static native long create_1();

    private static native void delete(long j);

    private static native int getPreFilterCap_0(long j);

    private static native int getPreFilterSize_0(long j);

    private static native int getPreFilterType_0(long j);

    private static native double[] getROI1_0(long j);

    private static native double[] getROI2_0(long j);

    private static native int getSmallerBlockSize_0(long j);

    private static native int getTextureThreshold_0(long j);

    private static native int getUniquenessRatio_0(long j);

    private static native void setPreFilterCap_0(long j, int i);

    private static native void setPreFilterSize_0(long j, int i);

    private static native void setPreFilterType_0(long j, int i);

    private static native void setROI1_0(long j, int i, int i2, int i3, int i4);

    private static native void setROI2_0(long j, int i, int i2, int i3, int i4);

    private static native void setSmallerBlockSize_0(long j, int i);

    private static native void setTextureThreshold_0(long j, int i);

    private static native void setUniquenessRatio_0(long j, int i);

    protected StereoBM(long addr) {
        super(addr);
    }

    public static StereoBM create(int numDisparities, int blockSize) {
        StereoBM retVal = new StereoBM(create_0(numDisparities, blockSize));
        return retVal;
    }

    public static StereoBM create() {
        StereoBM retVal = new StereoBM(create_1());
        return retVal;
    }

    public Rect getROI1() {
        Rect retVal = new Rect(getROI1_0(this.nativeObj));
        return retVal;
    }

    public Rect getROI2() {
        Rect retVal = new Rect(getROI2_0(this.nativeObj));
        return retVal;
    }

    public int getPreFilterCap() {
        int retVal = getPreFilterCap_0(this.nativeObj);
        return retVal;
    }

    public int getPreFilterSize() {
        int retVal = getPreFilterSize_0(this.nativeObj);
        return retVal;
    }

    public int getPreFilterType() {
        int retVal = getPreFilterType_0(this.nativeObj);
        return retVal;
    }

    public int getSmallerBlockSize() {
        int retVal = getSmallerBlockSize_0(this.nativeObj);
        return retVal;
    }

    public int getTextureThreshold() {
        int retVal = getTextureThreshold_0(this.nativeObj);
        return retVal;
    }

    public int getUniquenessRatio() {
        int retVal = getUniquenessRatio_0(this.nativeObj);
        return retVal;
    }

    public void setPreFilterCap(int preFilterCap) {
        setPreFilterCap_0(this.nativeObj, preFilterCap);
    }

    public void setPreFilterSize(int preFilterSize) {
        setPreFilterSize_0(this.nativeObj, preFilterSize);
    }

    public void setPreFilterType(int preFilterType) {
        setPreFilterType_0(this.nativeObj, preFilterType);
    }

    public void setROI1(Rect roi1) {
        setROI1_0(this.nativeObj, roi1.x, roi1.y, roi1.width, roi1.height);
    }

    public void setROI2(Rect roi2) {
        setROI2_0(this.nativeObj, roi2.x, roi2.y, roi2.width, roi2.height);
    }

    public void setSmallerBlockSize(int blockSize) {
        setSmallerBlockSize_0(this.nativeObj, blockSize);
    }

    public void setTextureThreshold(int textureThreshold) {
        setTextureThreshold_0(this.nativeObj, textureThreshold);
    }

    public void setUniquenessRatio(int uniquenessRatio) {
        setUniquenessRatio_0(this.nativeObj, uniquenessRatio);
    }

    @Override // org.opencv.calib3d.StereoMatcher, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
