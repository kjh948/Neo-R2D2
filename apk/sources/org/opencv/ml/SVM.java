package org.opencv.ml;

import org.opencv.core.Mat;
import org.opencv.core.TermCriteria;

/* JADX INFO: loaded from: classes.dex */
public class SVM extends StatModel {
    public static final int C = 0;
    public static final int CHI2 = 4;
    public static final int COEF = 4;
    public static final int CUSTOM = -1;
    public static final int C_SVC = 100;
    public static final int DEGREE = 5;
    public static final int EPS_SVR = 103;
    public static final int GAMMA = 1;
    public static final int INTER = 5;
    public static final int LINEAR = 0;
    public static final int NU = 3;
    public static final int NU_SVC = 101;
    public static final int NU_SVR = 104;
    public static final int ONE_CLASS = 102;
    public static final int P = 2;
    public static final int POLY = 1;
    public static final int RBF = 2;
    public static final int SIGMOID = 3;

    private static native long create_0();

    private static native void delete(long j);

    private static native double getC_0(long j);

    private static native long getClassWeights_0(long j);

    private static native double getCoef0_0(long j);

    private static native double getDecisionFunction_0(long j, int i, long j2, long j3);

    private static native long getDefaultGridPtr_0(int i);

    private static native double getDegree_0(long j);

    private static native double getGamma_0(long j);

    private static native int getKernelType_0(long j);

    private static native double getNu_0(long j);

    private static native double getP_0(long j);

    private static native long getSupportVectors_0(long j);

    private static native double[] getTermCriteria_0(long j);

    private static native int getType_0(long j);

    private static native long getUncompressedSupportVectors_0(long j);

    private static native long load_0(String str);

    private static native void setC_0(long j, double d);

    private static native void setClassWeights_0(long j, long j2);

    private static native void setCoef0_0(long j, double d);

    private static native void setDegree_0(long j, double d);

    private static native void setGamma_0(long j, double d);

    private static native void setKernel_0(long j, int i);

    private static native void setNu_0(long j, double d);

    private static native void setP_0(long j, double d);

    private static native void setTermCriteria_0(long j, int i, int i2, double d);

    private static native void setType_0(long j, int i);

    private static native boolean trainAuto_0(long j, long j2, int i, long j3, int i2, long j4, long j5, long j6, long j7, long j8, long j9, boolean z);

    private static native boolean trainAuto_1(long j, long j2, int i, long j3);

    protected SVM(long addr) {
        super(addr);
    }

    public Mat getClassWeights() {
        Mat retVal = new Mat(getClassWeights_0(this.nativeObj));
        return retVal;
    }

    public Mat getSupportVectors() {
        Mat retVal = new Mat(getSupportVectors_0(this.nativeObj));
        return retVal;
    }

    public Mat getUncompressedSupportVectors() {
        Mat retVal = new Mat(getUncompressedSupportVectors_0(this.nativeObj));
        return retVal;
    }

    public static ParamGrid getDefaultGridPtr(int param_id) {
        ParamGrid retVal = new ParamGrid(getDefaultGridPtr_0(param_id));
        return retVal;
    }

    public static SVM create() {
        SVM retVal = new SVM(create_0());
        return retVal;
    }

    public static SVM load(String filepath) {
        SVM retVal = new SVM(load_0(filepath));
        return retVal;
    }

    public TermCriteria getTermCriteria() {
        TermCriteria retVal = new TermCriteria(getTermCriteria_0(this.nativeObj));
        return retVal;
    }

    public boolean trainAuto(Mat samples, int layout, Mat responses, int kFold, ParamGrid Cgrid, ParamGrid gammaGrid, ParamGrid pGrid, ParamGrid nuGrid, ParamGrid coeffGrid, ParamGrid degreeGrid, boolean balanced) {
        boolean retVal = trainAuto_0(this.nativeObj, samples.nativeObj, layout, responses.nativeObj, kFold, Cgrid.getNativeObjAddr(), gammaGrid.getNativeObjAddr(), pGrid.getNativeObjAddr(), nuGrid.getNativeObjAddr(), coeffGrid.getNativeObjAddr(), degreeGrid.getNativeObjAddr(), balanced);
        return retVal;
    }

    public boolean trainAuto(Mat samples, int layout, Mat responses) {
        boolean retVal = trainAuto_1(this.nativeObj, samples.nativeObj, layout, responses.nativeObj);
        return retVal;
    }

    public double getC() {
        double retVal = getC_0(this.nativeObj);
        return retVal;
    }

    public double getCoef0() {
        double retVal = getCoef0_0(this.nativeObj);
        return retVal;
    }

    public double getDecisionFunction(int i, Mat alpha, Mat svidx) {
        double retVal = getDecisionFunction_0(this.nativeObj, i, alpha.nativeObj, svidx.nativeObj);
        return retVal;
    }

    public double getDegree() {
        double retVal = getDegree_0(this.nativeObj);
        return retVal;
    }

    public double getGamma() {
        double retVal = getGamma_0(this.nativeObj);
        return retVal;
    }

    public double getNu() {
        double retVal = getNu_0(this.nativeObj);
        return retVal;
    }

    public double getP() {
        double retVal = getP_0(this.nativeObj);
        return retVal;
    }

    public int getKernelType() {
        int retVal = getKernelType_0(this.nativeObj);
        return retVal;
    }

    public int getType() {
        int retVal = getType_0(this.nativeObj);
        return retVal;
    }

    public void setC(double val) {
        setC_0(this.nativeObj, val);
    }

    public void setClassWeights(Mat val) {
        setClassWeights_0(this.nativeObj, val.nativeObj);
    }

    public void setCoef0(double val) {
        setCoef0_0(this.nativeObj, val);
    }

    public void setDegree(double val) {
        setDegree_0(this.nativeObj, val);
    }

    public void setGamma(double val) {
        setGamma_0(this.nativeObj, val);
    }

    public void setKernel(int kernelType) {
        setKernel_0(this.nativeObj, kernelType);
    }

    public void setNu(double val) {
        setNu_0(this.nativeObj, val);
    }

    public void setP(double val) {
        setP_0(this.nativeObj, val);
    }

    public void setTermCriteria(TermCriteria val) {
        setTermCriteria_0(this.nativeObj, val.type, val.maxCount, val.epsilon);
    }

    public void setType(int val) {
        setType_0(this.nativeObj, val);
    }

    @Override // org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
