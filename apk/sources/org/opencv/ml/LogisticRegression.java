package org.opencv.ml;

import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

/* JADX INFO: loaded from: classes.dex */
public class LogisticRegression extends StatModel {
    public static final int BATCH = 0;
    public static final int MINI_BATCH = 1;
    public static final int REG_DISABLE = -1;
    public static final int REG_L1 = 0;
    public static final int REG_L2 = 1;

    private static native long create_0();

    private static native void delete(long j);

    private static native int getIterations_0(long j);

    private static native double getLearningRate_0(long j);

    private static native int getMiniBatchSize_0(long j);

    private static native int getRegularization_0(long j);

    private static native double[] getTermCriteria_0(long j);

    private static native int getTrainMethod_0(long j);

    private static native long get_learnt_thetas_0(long j);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native float predict_0(long j, long j2, long j3, int i);

    private static native float predict_1(long j, long j2);

    private static native void setIterations_0(long j, int i);

    private static native void setLearningRate_0(long j, double d);

    private static native void setMiniBatchSize_0(long j, int i);

    private static native void setRegularization_0(long j, int i);

    private static native void setTermCriteria_0(long j, int i, int i2, double d);

    private static native void setTrainMethod_0(long j, int i);

    protected LogisticRegression(long addr) {
        super(addr);
    }

    public Mat get_learnt_thetas() {
        Mat retVal = new Mat(get_learnt_thetas_0(this.nativeObj));
        return retVal;
    }

    public static LogisticRegression create() {
        LogisticRegression retVal = new LogisticRegression(create_0());
        return retVal;
    }

    public static LogisticRegression load(String filepath, String nodeName) {
        LogisticRegression retVal = new LogisticRegression(load_0(filepath, nodeName));
        return retVal;
    }

    public static LogisticRegression load(String filepath) {
        LogisticRegression retVal = new LogisticRegression(load_1(filepath));
        return retVal;
    }

    public TermCriteria getTermCriteria() {
        TermCriteria retVal = new TermCriteria(getTermCriteria_0(this.nativeObj));
        return retVal;
    }

    public double getLearningRate() {
        double retVal = getLearningRate_0(this.nativeObj);
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

    public int getIterations() {
        int retVal = getIterations_0(this.nativeObj);
        return retVal;
    }

    public int getMiniBatchSize() {
        int retVal = getMiniBatchSize_0(this.nativeObj);
        return retVal;
    }

    public int getRegularization() {
        int retVal = getRegularization_0(this.nativeObj);
        return retVal;
    }

    public int getTrainMethod() {
        int retVal = getTrainMethod_0(this.nativeObj);
        return retVal;
    }

    public void setIterations(int val) {
        setIterations_0(this.nativeObj, val);
    }

    public void setLearningRate(double val) {
        setLearningRate_0(this.nativeObj, val);
    }

    public void setMiniBatchSize(int val) {
        setMiniBatchSize_0(this.nativeObj, val);
    }

    public void setRegularization(int val) {
        setRegularization_0(this.nativeObj, val);
    }

    public void setTermCriteria(TermCriteria val) {
        setTermCriteria_0(this.nativeObj, val.type, val.maxCount, val.epsilon);
    }

    public void setTrainMethod(int val) {
        setTrainMethod_0(this.nativeObj, val);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
