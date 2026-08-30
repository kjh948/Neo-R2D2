package org.opencv.ml;

/* JADX INFO: loaded from: classes.dex */
public class ParamGrid {
    protected final long nativeObj;

    private static native long create_0(double d, double d2, double d3);

    private static native long create_1();

    private static native void delete(long j);

    private static native double get_logStep_0(long j);

    private static native double get_maxVal_0(long j);

    private static native double get_minVal_0(long j);

    private static native void set_logStep_0(long j, double d);

    private static native void set_maxVal_0(long j, double d);

    private static native void set_minVal_0(long j, double d);

    protected ParamGrid(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public static ParamGrid create(double minVal, double maxVal, double logstep) {
        ParamGrid retVal = new ParamGrid(create_0(minVal, maxVal, logstep));
        return retVal;
    }

    public static ParamGrid create() {
        ParamGrid retVal = new ParamGrid(create_1());
        return retVal;
    }

    public double get_minVal() {
        double retVal = get_minVal_0(this.nativeObj);
        return retVal;
    }

    public void set_minVal(double minVal) {
        set_minVal_0(this.nativeObj, minVal);
    }

    public double get_maxVal() {
        double retVal = get_maxVal_0(this.nativeObj);
        return retVal;
    }

    public void set_maxVal(double maxVal) {
        set_maxVal_0(this.nativeObj, maxVal);
    }

    public double get_logStep() {
        double retVal = get_logStep_0(this.nativeObj);
        return retVal;
    }

    public void set_logStep(double logStep) {
        set_logStep_0(this.nativeObj, logStep);
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
