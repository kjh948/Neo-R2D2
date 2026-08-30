package org.opencv.photo;

import java.util.List;
import org.opencv.core.Algorithm;
import org.opencv.core.Mat;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class AlignExposures extends Algorithm {
    private static native void delete(long j);

    private static native void process_0(long j, long j2, long j3, long j4, long j5);

    protected AlignExposures(long addr) {
        super(addr);
    }

    public void process(List<Mat> src, List<Mat> dst, Mat times, Mat response) {
        Mat src_mat = Converters.vector_Mat_to_Mat(src);
        Mat dst_mat = Converters.vector_Mat_to_Mat(dst);
        process_0(this.nativeObj, src_mat.nativeObj, dst_mat.nativeObj, times.nativeObj, response.nativeObj);
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
