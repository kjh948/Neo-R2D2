package org.opencv.ml;

import java.util.List;
import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;
import org.opencv.utils.Converters;

/* JADX INFO: loaded from: classes.dex */
public class EM extends StatModel {
    public static final int COV_MAT_DEFAULT = 1;
    public static final int COV_MAT_DIAGONAL = 1;
    public static final int COV_MAT_GENERIC = 2;
    public static final int COV_MAT_SPHERICAL = 0;
    public static final int DEFAULT_MAX_ITERS = 100;
    public static final int DEFAULT_NCLUSTERS = 5;
    public static final int START_AUTO_STEP = 0;
    public static final int START_E_STEP = 1;
    public static final int START_M_STEP = 2;

    private static native long create_0();

    private static native void delete(long j);

    private static native int getClustersNumber_0(long j);

    private static native int getCovarianceMatrixType_0(long j);

    private static native void getCovs_0(long j, long j2);

    private static native long getMeans_0(long j);

    private static native double[] getTermCriteria_0(long j);

    private static native long getWeights_0(long j);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native double[] predict2_0(long j, long j2, long j3);

    private static native float predict_0(long j, long j2, long j3, int i);

    private static native float predict_1(long j, long j2);

    private static native void setClustersNumber_0(long j, int i);

    private static native void setCovarianceMatrixType_0(long j, int i);

    private static native void setTermCriteria_0(long j, int i, int i2, double d);

    private static native boolean trainEM_0(long j, long j2, long j3, long j4, long j5);

    private static native boolean trainEM_1(long j, long j2);

    private static native boolean trainE_0(long j, long j2, long j3, long j4, long j5, long j6, long j7, long j8);

    private static native boolean trainE_1(long j, long j2, long j3);

    private static native boolean trainM_0(long j, long j2, long j3, long j4, long j5, long j6);

    private static native boolean trainM_1(long j, long j2, long j3);

    protected EM(long addr) {
        super(addr);
    }

    public Mat getMeans() {
        Mat retVal = new Mat(getMeans_0(this.nativeObj));
        return retVal;
    }

    public Mat getWeights() {
        Mat retVal = new Mat(getWeights_0(this.nativeObj));
        return retVal;
    }

    public static EM create() {
        EM retVal = new EM(create_0());
        return retVal;
    }

    public static EM load(String filepath, String nodeName) {
        EM retVal = new EM(load_0(filepath, nodeName));
        return retVal;
    }

    public static EM load(String filepath) {
        EM retVal = new EM(load_1(filepath));
        return retVal;
    }

    public TermCriteria getTermCriteria() {
        TermCriteria retVal = new TermCriteria(getTermCriteria_0(this.nativeObj));
        return retVal;
    }

    public double[] predict2(Mat sample, Mat probs) {
        double[] retVal = predict2_0(this.nativeObj, sample.nativeObj, probs.nativeObj);
        return retVal;
    }

    public boolean trainE(Mat samples, Mat means0, Mat covs0, Mat weights0, Mat logLikelihoods, Mat labels, Mat probs) {
        boolean retVal = trainE_0(this.nativeObj, samples.nativeObj, means0.nativeObj, covs0.nativeObj, weights0.nativeObj, logLikelihoods.nativeObj, labels.nativeObj, probs.nativeObj);
        return retVal;
    }

    public boolean trainE(Mat samples, Mat means0) {
        boolean retVal = trainE_1(this.nativeObj, samples.nativeObj, means0.nativeObj);
        return retVal;
    }

    public boolean trainEM(Mat samples, Mat logLikelihoods, Mat labels, Mat probs) {
        boolean retVal = trainEM_0(this.nativeObj, samples.nativeObj, logLikelihoods.nativeObj, labels.nativeObj, probs.nativeObj);
        return retVal;
    }

    public boolean trainEM(Mat samples) {
        boolean retVal = trainEM_1(this.nativeObj, samples.nativeObj);
        return retVal;
    }

    public boolean trainM(Mat samples, Mat probs0, Mat logLikelihoods, Mat labels, Mat probs) {
        boolean retVal = trainM_0(this.nativeObj, samples.nativeObj, probs0.nativeObj, logLikelihoods.nativeObj, labels.nativeObj, probs.nativeObj);
        return retVal;
    }

    public boolean trainM(Mat samples, Mat probs0) {
        boolean retVal = trainM_1(this.nativeObj, samples.nativeObj, probs0.nativeObj);
        return retVal;
    }

    @Override // org.opencv.ml.StatModel
    public float predict(Mat samples, Mat results, int flags) {
        float retVal = predict_0(this.nativeObj, samples.nativeObj, results.nativeObj, flags);
        return retVal;
    }

    @Override // org.opencv.ml.StatModel
    public float predict(Mat samples) {
        float retVal = predict_1(this.nativeObj, samples.nativeObj);
        return retVal;
    }

    public int getClustersNumber() {
        int retVal = getClustersNumber_0(this.nativeObj);
        return retVal;
    }

    public int getCovarianceMatrixType() {
        int retVal = getCovarianceMatrixType_0(this.nativeObj);
        return retVal;
    }

    public void getCovs(List<Mat> covs) {
        Mat covs_mat = new Mat();
        getCovs_0(this.nativeObj, covs_mat.nativeObj);
        Converters.Mat_to_vector_Mat(covs_mat, covs);
        covs_mat.release();
    }

    public void setClustersNumber(int val) {
        setClustersNumber_0(this.nativeObj, val);
    }

    public void setCovarianceMatrixType(int val) {
        setCovarianceMatrixType_0(this.nativeObj, val);
    }

    public void setTermCriteria(TermCriteria val) {
        setTermCriteria_0(this.nativeObj, val.type, val.maxCount, val.epsilon);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
