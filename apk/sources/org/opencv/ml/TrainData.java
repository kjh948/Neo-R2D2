package org.opencv.ml;

import java.util.List;
import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class TrainData {
    protected final long nativeObj;

    private static native long create_0(long j, int i, long j2, long j3, long j4, long j5, long j6);

    private static native long create_1(long j, int i, long j2);

    private static native void delete(long j);

    private static native int getCatCount_0(long j, int i);

    private static native long getCatMap_0(long j);

    private static native long getCatOfs_0(long j);

    private static native long getClassLabels_0(long j);

    private static native long getDefaultSubstValues_0(long j);

    private static native int getLayout_0(long j);

    private static native long getMissing_0(long j);

    private static native int getNAllVars_0(long j);

    private static native int getNSamples_0(long j);

    private static native int getNTestSamples_0(long j);

    private static native int getNTrainSamples_0(long j);

    private static native int getNVars_0(long j);

    private static native void getNames_0(long j, List<String> list);

    private static native long getNormCatResponses_0(long j);

    private static native int getResponseType_0(long j);

    private static native long getResponses_0(long j);

    private static native long getSampleWeights_0(long j);

    private static native void getSample_0(long j, long j2, int i, float f);

    private static native long getSamples_0(long j);

    private static native long getSubVector_0(long j, long j2);

    private static native long getTestNormCatResponses_0(long j);

    private static native long getTestResponses_0(long j);

    private static native long getTestSampleIdx_0(long j);

    private static native long getTestSampleWeights_0(long j);

    private static native long getTestSamples_0(long j);

    private static native long getTrainNormCatResponses_0(long j);

    private static native long getTrainResponses_0(long j);

    private static native long getTrainSampleIdx_0(long j);

    private static native long getTrainSampleWeights_0(long j);

    private static native long getTrainSamples_0(long j, int i, boolean z, boolean z2);

    private static native long getTrainSamples_1(long j);

    private static native void getValues_0(long j, int i, long j2, float f);

    private static native long getVarIdx_0(long j);

    private static native long getVarSymbolFlags_0(long j);

    private static native long getVarType_0(long j);

    private static native void setTrainTestSplitRatio_0(long j, double d, boolean z);

    private static native void setTrainTestSplitRatio_1(long j, double d);

    private static native void setTrainTestSplit_0(long j, int i, boolean z);

    private static native void setTrainTestSplit_1(long j, int i);

    private static native void shuffleTrainTest_0(long j);

    protected TrainData(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public Mat getCatMap() {
        Mat retVal = new Mat(getCatMap_0(this.nativeObj));
        return retVal;
    }

    public Mat getCatOfs() {
        Mat retVal = new Mat(getCatOfs_0(this.nativeObj));
        return retVal;
    }

    public Mat getClassLabels() {
        Mat retVal = new Mat(getClassLabels_0(this.nativeObj));
        return retVal;
    }

    public Mat getDefaultSubstValues() {
        Mat retVal = new Mat(getDefaultSubstValues_0(this.nativeObj));
        return retVal;
    }

    public Mat getMissing() {
        Mat retVal = new Mat(getMissing_0(this.nativeObj));
        return retVal;
    }

    public Mat getNormCatResponses() {
        Mat retVal = new Mat(getNormCatResponses_0(this.nativeObj));
        return retVal;
    }

    public Mat getResponses() {
        Mat retVal = new Mat(getResponses_0(this.nativeObj));
        return retVal;
    }

    public Mat getSampleWeights() {
        Mat retVal = new Mat(getSampleWeights_0(this.nativeObj));
        return retVal;
    }

    public Mat getSamples() {
        Mat retVal = new Mat(getSamples_0(this.nativeObj));
        return retVal;
    }

    public static Mat getSubVector(Mat vec, Mat idx) {
        Mat retVal = new Mat(getSubVector_0(vec.nativeObj, idx.nativeObj));
        return retVal;
    }

    public Mat getTestNormCatResponses() {
        Mat retVal = new Mat(getTestNormCatResponses_0(this.nativeObj));
        return retVal;
    }

    public Mat getTestResponses() {
        Mat retVal = new Mat(getTestResponses_0(this.nativeObj));
        return retVal;
    }

    public Mat getTestSampleIdx() {
        Mat retVal = new Mat(getTestSampleIdx_0(this.nativeObj));
        return retVal;
    }

    public Mat getTestSampleWeights() {
        Mat retVal = new Mat(getTestSampleWeights_0(this.nativeObj));
        return retVal;
    }

    public Mat getTestSamples() {
        Mat retVal = new Mat(getTestSamples_0(this.nativeObj));
        return retVal;
    }

    public Mat getTrainNormCatResponses() {
        Mat retVal = new Mat(getTrainNormCatResponses_0(this.nativeObj));
        return retVal;
    }

    public Mat getTrainResponses() {
        Mat retVal = new Mat(getTrainResponses_0(this.nativeObj));
        return retVal;
    }

    public Mat getTrainSampleIdx() {
        Mat retVal = new Mat(getTrainSampleIdx_0(this.nativeObj));
        return retVal;
    }

    public Mat getTrainSampleWeights() {
        Mat retVal = new Mat(getTrainSampleWeights_0(this.nativeObj));
        return retVal;
    }

    public Mat getTrainSamples(int layout, boolean compressSamples, boolean compressVars) {
        Mat retVal = new Mat(getTrainSamples_0(this.nativeObj, layout, compressSamples, compressVars));
        return retVal;
    }

    public Mat getTrainSamples() {
        Mat retVal = new Mat(getTrainSamples_1(this.nativeObj));
        return retVal;
    }

    public Mat getVarIdx() {
        Mat retVal = new Mat(getVarIdx_0(this.nativeObj));
        return retVal;
    }

    public Mat getVarSymbolFlags() {
        Mat retVal = new Mat(getVarSymbolFlags_0(this.nativeObj));
        return retVal;
    }

    public Mat getVarType() {
        Mat retVal = new Mat(getVarType_0(this.nativeObj));
        return retVal;
    }

    public static TrainData create(Mat samples, int layout, Mat responses, Mat varIdx, Mat sampleIdx, Mat sampleWeights, Mat varType) {
        TrainData retVal = new TrainData(create_0(samples.nativeObj, layout, responses.nativeObj, varIdx.nativeObj, sampleIdx.nativeObj, sampleWeights.nativeObj, varType.nativeObj));
        return retVal;
    }

    public static TrainData create(Mat samples, int layout, Mat responses) {
        TrainData retVal = new TrainData(create_1(samples.nativeObj, layout, responses.nativeObj));
        return retVal;
    }

    public int getCatCount(int vi) {
        int retVal = getCatCount_0(this.nativeObj, vi);
        return retVal;
    }

    public int getLayout() {
        int retVal = getLayout_0(this.nativeObj);
        return retVal;
    }

    public int getNAllVars() {
        int retVal = getNAllVars_0(this.nativeObj);
        return retVal;
    }

    public int getNSamples() {
        int retVal = getNSamples_0(this.nativeObj);
        return retVal;
    }

    public int getNTestSamples() {
        int retVal = getNTestSamples_0(this.nativeObj);
        return retVal;
    }

    public int getNTrainSamples() {
        int retVal = getNTrainSamples_0(this.nativeObj);
        return retVal;
    }

    public int getNVars() {
        int retVal = getNVars_0(this.nativeObj);
        return retVal;
    }

    public int getResponseType() {
        int retVal = getResponseType_0(this.nativeObj);
        return retVal;
    }

    public void getNames(List<String> names) {
        getNames_0(this.nativeObj, names);
    }

    public void getSample(Mat varIdx, int sidx, float buf) {
        getSample_0(this.nativeObj, varIdx.nativeObj, sidx, buf);
    }

    public void getValues(int vi, Mat sidx, float values) {
        getValues_0(this.nativeObj, vi, sidx.nativeObj, values);
    }

    public void setTrainTestSplit(int count, boolean shuffle) {
        setTrainTestSplit_0(this.nativeObj, count, shuffle);
    }

    public void setTrainTestSplit(int count) {
        setTrainTestSplit_1(this.nativeObj, count);
    }

    public void setTrainTestSplitRatio(double ratio, boolean shuffle) {
        setTrainTestSplitRatio_0(this.nativeObj, ratio, shuffle);
    }

    public void setTrainTestSplitRatio(double ratio) {
        setTrainTestSplitRatio_1(this.nativeObj, ratio);
    }

    public void shuffleTrainTest() {
        shuffleTrainTest_0(this.nativeObj);
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
