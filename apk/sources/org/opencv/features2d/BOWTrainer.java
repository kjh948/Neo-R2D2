package org.opencv.features2d;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class BOWTrainer {
    protected final long nativeObj;

    private static native void add_0(long j, long j2);

    private static native void clear_0(long j);

    private static native long cluster_0(long j, long j2);

    private static native long cluster_1(long j);

    private static native void delete(long j);

    private static native int descriptorsCount_0(long j);

    private static native long getDescriptors_0(long j);

    protected BOWTrainer(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public Mat cluster(Mat descriptors) {
        Mat retVal = new Mat(cluster_0(this.nativeObj, descriptors.nativeObj));
        return retVal;
    }

    public Mat cluster() {
        Mat retVal = new Mat(cluster_1(this.nativeObj));
        return retVal;
    }

    public int descriptorsCount() {
        int retVal = descriptorsCount_0(this.nativeObj);
        return retVal;
    }

    public List<Mat> getDescriptors() {
        List<Mat> retVal = new ArrayList<>();
        Mat retValMat = new Mat(getDescriptors_0(this.nativeObj));
        Converters.Mat_to_vector_Mat(retValMat, retVal);
        return retVal;
    }

    public void add(Mat descriptors) {
        add_0(this.nativeObj, descriptors.nativeObj);
    }

    public void clear() {
        clear_0(this.nativeObj);
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
