package org.opencv.ml;

import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class NormalBayesClassifier extends StatModel {
    private static native long create_0();

    private static native void delete(long j);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native float predictProb_0(long j, long j2, long j3, long j4, int i);

    private static native float predictProb_1(long j, long j2, long j3, long j4);

    protected NormalBayesClassifier(long addr) {
        super(addr);
    }

    public static NormalBayesClassifier create() {
        NormalBayesClassifier retVal = new NormalBayesClassifier(create_0());
        return retVal;
    }

    public static NormalBayesClassifier load(String filepath, String nodeName) {
        NormalBayesClassifier retVal = new NormalBayesClassifier(load_0(filepath, nodeName));
        return retVal;
    }

    public static NormalBayesClassifier load(String filepath) {
        NormalBayesClassifier retVal = new NormalBayesClassifier(load_1(filepath));
        return retVal;
    }

    public float predictProb(Mat inputs, Mat outputs, Mat outputProbs, int flags) {
        float retVal = predictProb_0(this.nativeObj, inputs.nativeObj, outputs.nativeObj, outputProbs.nativeObj, flags);
        return retVal;
    }

    public float predictProb(Mat inputs, Mat outputs, Mat outputProbs) {
        float retVal = predictProb_1(this.nativeObj, inputs.nativeObj, outputs.nativeObj, outputProbs.nativeObj);
        return retVal;
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
