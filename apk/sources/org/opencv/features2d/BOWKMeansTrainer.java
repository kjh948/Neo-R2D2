package org.opencv.features2d;

import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

/* JADX INFO: loaded from: classes.dex */
public class BOWKMeansTrainer extends BOWTrainer {
    private static native long BOWKMeansTrainer_0(int i, int i2, int i3, double d, int i4, int i5);

    private static native long BOWKMeansTrainer_1(int i);

    private static native long cluster_0(long j, long j2);

    private static native long cluster_1(long j);

    private static native void delete(long j);

    protected BOWKMeansTrainer(long addr) {
        super(addr);
    }

    public BOWKMeansTrainer(int clusterCount, TermCriteria termcrit, int attempts, int flags) {
        super(BOWKMeansTrainer_0(clusterCount, termcrit.type, termcrit.maxCount, termcrit.epsilon, attempts, flags));
    }

    public BOWKMeansTrainer(int clusterCount) {
        super(BOWKMeansTrainer_1(clusterCount));
    }

    @Override // org.opencv.features2d.BOWTrainer
    public Mat cluster(Mat descriptors) {
        Mat retVal = new Mat(cluster_0(this.nativeObj, descriptors.nativeObj));
        return retVal;
    }

    @Override // org.opencv.features2d.BOWTrainer
    public Mat cluster() {
        Mat retVal = new Mat(cluster_1(this.nativeObj));
        return retVal;
    }

    @Override // org.opencv.features2d.BOWTrainer
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
