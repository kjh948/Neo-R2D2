package org.opencv.features2d;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class MSER extends Feature2D {
    private static native long create_0(int i, int i2, int i3, double d, double d2, int i4, double d3, double d4, int i5);

    private static native long create_1();

    private static native void delete(long j);

    private static native void detectRegions_0(long j, long j2, long j3, long j4);

    private static native int getDelta_0(long j);

    private static native int getMaxArea_0(long j);

    private static native int getMinArea_0(long j);

    private static native boolean getPass2Only_0(long j);

    private static native void setDelta_0(long j, int i);

    private static native void setMaxArea_0(long j, int i);

    private static native void setMinArea_0(long j, int i);

    private static native void setPass2Only_0(long j, boolean z);

    protected MSER(long addr) {
        super(addr);
    }

    public static MSER create(int _delta, int _min_area, int _max_area, double _max_variation, double _min_diversity, int _max_evolution, double _area_threshold, double _min_margin, int _edge_blur_size) {
        MSER retVal = new MSER(create_0(_delta, _min_area, _max_area, _max_variation, _min_diversity, _max_evolution, _area_threshold, _min_margin, _edge_blur_size));
        return retVal;
    }

    public static MSER create() {
        MSER retVal = new MSER(create_1());
        return retVal;
    }

    public boolean getPass2Only() {
        boolean retVal = getPass2Only_0(this.nativeObj);
        return retVal;
    }

    public int getDelta() {
        int retVal = getDelta_0(this.nativeObj);
        return retVal;
    }

    public int getMaxArea() {
        int retVal = getMaxArea_0(this.nativeObj);
        return retVal;
    }

    public int getMinArea() {
        int retVal = getMinArea_0(this.nativeObj);
        return retVal;
    }

    public void detectRegions(Mat image, List<MatOfPoint> msers, MatOfRect bboxes) {
        Mat msers_mat = new Mat();
        detectRegions_0(this.nativeObj, image.nativeObj, msers_mat.nativeObj, bboxes.nativeObj);
        Converters.Mat_to_vector_vector_Point(msers_mat, msers);
        msers_mat.release();
    }

    public void setDelta(int delta) {
        setDelta_0(this.nativeObj, delta);
    }

    public void setMaxArea(int maxArea) {
        setMaxArea_0(this.nativeObj, maxArea);
    }

    public void setMinArea(int minArea) {
        setMinArea_0(this.nativeObj, minArea);
    }

    public void setPass2Only(boolean f) {
        setPass2Only_0(this.nativeObj, f);
    }

    @Override // org.opencv.features2d.Feature2D, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
