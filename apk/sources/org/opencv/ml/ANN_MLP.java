package org.opencv.ml;

import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

/* JADX INFO: loaded from: classes.dex */
public class ANN_MLP extends StatModel {
    public static final int BACKPROP = 0;
    public static final int GAUSSIAN = 2;
    public static final int IDENTITY = 0;
    public static final int NO_INPUT_SCALE = 2;
    public static final int NO_OUTPUT_SCALE = 4;
    public static final int RPROP = 1;
    public static final int SIGMOID_SYM = 1;
    public static final int UPDATE_WEIGHTS = 1;

    private static native long create_0();

    private static native void delete(long j);

    private static native double getBackpropMomentumScale_0(long j);

    private static native double getBackpropWeightScale_0(long j);

    private static native long getLayerSizes_0(long j);

    private static native double getRpropDW0_0(long j);

    private static native double getRpropDWMax_0(long j);

    private static native double getRpropDWMin_0(long j);

    private static native double getRpropDWMinus_0(long j);

    private static native double getRpropDWPlus_0(long j);

    private static native double[] getTermCriteria_0(long j);

    private static native int getTrainMethod_0(long j);

    private static native long getWeights_0(long j, int i);

    private static native long load_0(String str);

    private static native void setActivationFunction_0(long j, int i, double d, double d2);

    private static native void setActivationFunction_1(long j, int i);

    private static native void setBackpropMomentumScale_0(long j, double d);

    private static native void setBackpropWeightScale_0(long j, double d);

    private static native void setLayerSizes_0(long j, long j2);

    private static native void setRpropDW0_0(long j, double d);

    private static native void setRpropDWMax_0(long j, double d);

    private static native void setRpropDWMin_0(long j, double d);

    private static native void setRpropDWMinus_0(long j, double d);

    private static native void setRpropDWPlus_0(long j, double d);

    private static native void setTermCriteria_0(long j, int i, int i2, double d);

    private static native void setTrainMethod_0(long j, int i, double d, double d2);

    private static native void setTrainMethod_1(long j, int i);

    protected ANN_MLP(long addr) {
        super(addr);
    }

    public Mat getLayerSizes() {
        Mat retVal = new Mat(getLayerSizes_0(this.nativeObj));
        return retVal;
    }

    public Mat getWeights(int layerIdx) {
        Mat retVal = new Mat(getWeights_0(this.nativeObj, layerIdx));
        return retVal;
    }

    public static ANN_MLP create() {
        ANN_MLP retVal = new ANN_MLP(create_0());
        return retVal;
    }

    public static ANN_MLP load(String filepath) {
        ANN_MLP retVal = new ANN_MLP(load_0(filepath));
        return retVal;
    }

    public TermCriteria getTermCriteria() {
        TermCriteria retVal = new TermCriteria(getTermCriteria_0(this.nativeObj));
        return retVal;
    }

    public double getBackpropMomentumScale() {
        double retVal = getBackpropMomentumScale_0(this.nativeObj);
        return retVal;
    }

    public double getBackpropWeightScale() {
        double retVal = getBackpropWeightScale_0(this.nativeObj);
        return retVal;
    }

    public double getRpropDW0() {
        double retVal = getRpropDW0_0(this.nativeObj);
        return retVal;
    }

    public double getRpropDWMax() {
        double retVal = getRpropDWMax_0(this.nativeObj);
        return retVal;
    }

    public double getRpropDWMin() {
        double retVal = getRpropDWMin_0(this.nativeObj);
        return retVal;
    }

    public double getRpropDWMinus() {
        double retVal = getRpropDWMinus_0(this.nativeObj);
        return retVal;
    }

    public double getRpropDWPlus() {
        double retVal = getRpropDWPlus_0(this.nativeObj);
        return retVal;
    }

    public int getTrainMethod() {
        int retVal = getTrainMethod_0(this.nativeObj);
        return retVal;
    }

    public void setActivationFunction(int type, double param1, double param2) {
        setActivationFunction_0(this.nativeObj, type, param1, param2);
    }

    public void setActivationFunction(int type) {
        setActivationFunction_1(this.nativeObj, type);
    }

    public void setBackpropMomentumScale(double val) {
        setBackpropMomentumScale_0(this.nativeObj, val);
    }

    public void setBackpropWeightScale(double val) {
        setBackpropWeightScale_0(this.nativeObj, val);
    }

    public void setLayerSizes(Mat _layer_sizes) {
        setLayerSizes_0(this.nativeObj, _layer_sizes.nativeObj);
    }

    public void setRpropDW0(double val) {
        setRpropDW0_0(this.nativeObj, val);
    }

    public void setRpropDWMax(double val) {
        setRpropDWMax_0(this.nativeObj, val);
    }

    public void setRpropDWMin(double val) {
        setRpropDWMin_0(this.nativeObj, val);
    }

    public void setRpropDWMinus(double val) {
        setRpropDWMinus_0(this.nativeObj, val);
    }

    public void setRpropDWPlus(double val) {
        setRpropDWPlus_0(this.nativeObj, val);
    }

    public void setTermCriteria(TermCriteria val) {
        setTermCriteria_0(this.nativeObj, val.type, val.maxCount, val.epsilon);
    }

    public void setTrainMethod(int method, double param1, double param2) {
        setTrainMethod_0(this.nativeObj, method, param1, param2);
    }

    public void setTrainMethod(int method) {
        setTrainMethod_1(this.nativeObj, method);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
