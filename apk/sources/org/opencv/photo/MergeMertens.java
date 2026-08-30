package org.opencv.photo;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class MergeMertens extends MergeExposures {
    private static native void delete(long j);

    private static native float getContrastWeight_0(long j);

    private static native float getExposureWeight_0(long j);

    private static native float getSaturationWeight_0(long j);

    private static native void process_0(long j, long j2, long j3, long j4, long j5);

    private static native void process_1(long j, long j2, long j3);

    private static native void setContrastWeight_0(long j, float f);

    private static native void setExposureWeight_0(long j, float f);

    private static native void setSaturationWeight_0(long j, float f);

    protected MergeMertens(long addr) {
        super(addr);
    }

    public float getContrastWeight() {
        float retVal = getContrastWeight_0(this.nativeObj);
        return retVal;
    }

    public float getExposureWeight() {
        float retVal = getExposureWeight_0(this.nativeObj);
        return retVal;
    }

    public float getSaturationWeight() {
        float retVal = getSaturationWeight_0(this.nativeObj);
        return retVal;
    }

    @Override // org.opencv.photo.MergeExposures
    public void process(List<Mat> src, Mat dst, Mat times, Mat response) {
        Mat src_mat = Converters.vector_Mat_to_Mat(src);
        process_0(this.nativeObj, src_mat.nativeObj, dst.nativeObj, times.nativeObj, response.nativeObj);
    }

    public void process(List<Mat> src, Mat dst) {
        Mat src_mat = Converters.vector_Mat_to_Mat(src);
        process_1(this.nativeObj, src_mat.nativeObj, dst.nativeObj);
    }

    public void setContrastWeight(float contrast_weiht) {
        setContrastWeight_0(this.nativeObj, contrast_weiht);
    }

    public void setExposureWeight(float exposure_weight) {
        setExposureWeight_0(this.nativeObj, exposure_weight);
    }

    public void setSaturationWeight(float saturation_weight) {
        setSaturationWeight_0(this.nativeObj, saturation_weight);
    }

    @Override // org.opencv.photo.MergeExposures, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
