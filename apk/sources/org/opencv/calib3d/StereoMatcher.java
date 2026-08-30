package org.opencv.calib3d;

import org.opencv.core.Algorithm;
import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class StereoMatcher extends Algorithm {
    public static final int DISP_SCALE = 16;
    public static final int DISP_SHIFT = 4;

    private static native void compute_0(long j, long j2, long j3, long j4);

    private static native void delete(long j);

    private static native int getBlockSize_0(long j);

    private static native int getDisp12MaxDiff_0(long j);

    private static native int getMinDisparity_0(long j);

    private static native int getNumDisparities_0(long j);

    private static native int getSpeckleRange_0(long j);

    private static native int getSpeckleWindowSize_0(long j);

    private static native void setBlockSize_0(long j, int i);

    private static native void setDisp12MaxDiff_0(long j, int i);

    private static native void setMinDisparity_0(long j, int i);

    private static native void setNumDisparities_0(long j, int i);

    private static native void setSpeckleRange_0(long j, int i);

    private static native void setSpeckleWindowSize_0(long j, int i);

    protected StereoMatcher(long addr) {
        super(addr);
    }

    public int getBlockSize() {
        int retVal = getBlockSize_0(this.nativeObj);
        return retVal;
    }

    public int getDisp12MaxDiff() {
        int retVal = getDisp12MaxDiff_0(this.nativeObj);
        return retVal;
    }

    public int getMinDisparity() {
        int retVal = getMinDisparity_0(this.nativeObj);
        return retVal;
    }

    public int getNumDisparities() {
        int retVal = getNumDisparities_0(this.nativeObj);
        return retVal;
    }

    public int getSpeckleRange() {
        int retVal = getSpeckleRange_0(this.nativeObj);
        return retVal;
    }

    public int getSpeckleWindowSize() {
        int retVal = getSpeckleWindowSize_0(this.nativeObj);
        return retVal;
    }

    public void compute(Mat left, Mat right, Mat disparity) {
        compute_0(this.nativeObj, left.nativeObj, right.nativeObj, disparity.nativeObj);
    }

    public void setBlockSize(int blockSize) {
        setBlockSize_0(this.nativeObj, blockSize);
    }

    public void setDisp12MaxDiff(int disp12MaxDiff) {
        setDisp12MaxDiff_0(this.nativeObj, disp12MaxDiff);
    }

    public void setMinDisparity(int minDisparity) {
        setMinDisparity_0(this.nativeObj, minDisparity);
    }

    public void setNumDisparities(int numDisparities) {
        setNumDisparities_0(this.nativeObj, numDisparities);
    }

    public void setSpeckleRange(int speckleRange) {
        setSpeckleRange_0(this.nativeObj, speckleRange);
    }

    public void setSpeckleWindowSize(int speckleWindowSize) {
        setSpeckleWindowSize_0(this.nativeObj, speckleWindowSize);
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
