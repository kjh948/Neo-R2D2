package org.opencv.photo;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class MergeRobertson extends MergeExposures {
    private static native void delete(long j);

    private static native void process_0(long j, long j2, long j3, long j4, long j5);

    private static native void process_1(long j, long j2, long j3, long j4);

    protected MergeRobertson(long addr) {
        super(addr);
    }

    @Override // org.opencv.photo.MergeExposures
    public void process(List<Mat> src, Mat dst, Mat times, Mat response) {
        Mat src_mat = Converters.vector_Mat_to_Mat(src);
        process_0(this.nativeObj, src_mat.nativeObj, dst.nativeObj, times.nativeObj, response.nativeObj);
    }

    public void process(List<Mat> src, Mat dst, Mat times) {
        Mat src_mat = Converters.vector_Mat_to_Mat(src);
        process_1(this.nativeObj, src_mat.nativeObj, dst.nativeObj, times.nativeObj);
    }

    @Override // org.opencv.photo.MergeExposures, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
