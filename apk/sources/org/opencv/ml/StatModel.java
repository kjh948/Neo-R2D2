package org.opencv.ml;

import org.opencv.core.Algorithm;
import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class StatModel extends Algorithm {
    public static final int COMPRESSED_INPUT = 2;
    public static final int PREPROCESSED_INPUT = 4;
    public static final int RAW_OUTPUT = 1;
    public static final int UPDATE_MODEL = 1;

    private static native float calcError_0(long j, long j2, boolean z, long j3);

    private static native void delete(long j);

    private static native boolean empty_0(long j);

    private static native int getVarCount_0(long j);

    private static native boolean isClassifier_0(long j);

    private static native boolean isTrained_0(long j);

    private static native float predict_0(long j, long j2, long j3, int i);

    private static native float predict_1(long j, long j2);

    private static native boolean train_0(long j, long j2, int i, long j3);

    private static native boolean train_1(long j, long j2, int i);

    private static native boolean train_2(long j, long j2);

    protected StatModel(long addr) {
        super(addr);
    }

    public boolean empty() {
        boolean retVal = empty_0(this.nativeObj);
        return retVal;
    }

    public boolean isClassifier() {
        boolean retVal = isClassifier_0(this.nativeObj);
        return retVal;
    }

    public boolean isTrained() {
        boolean retVal = isTrained_0(this.nativeObj);
        return retVal;
    }

    public boolean train(Mat samples, int layout, Mat responses) {
        boolean retVal = train_0(this.nativeObj, samples.nativeObj, layout, responses.nativeObj);
        return retVal;
    }

    public boolean train(TrainData trainData, int flags) {
        boolean retVal = train_1(this.nativeObj, trainData.getNativeObjAddr(), flags);
        return retVal;
    }

    public boolean train(TrainData trainData) {
        boolean retVal = train_2(this.nativeObj, trainData.getNativeObjAddr());
        return retVal;
    }

    public float calcError(TrainData data, boolean test, Mat resp) {
        float retVal = calcError_0(this.nativeObj, data.getNativeObjAddr(), test, resp.nativeObj);
        return retVal;
    }

    public float predict(Mat samples, Mat results, int flags) {
        float retVal = predict_0(this.nativeObj, samples.nativeObj, results.nativeObj, flags);
        return retVal;
    }

    public float predict(Mat samples) {
        float retVal = predict_1(this.nativeObj, samples.nativeObj);
        return retVal;
    }

    public int getVarCount() {
        int retVal = getVarCount_0(this.nativeObj);
        return retVal;
    }

    @Override // org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
