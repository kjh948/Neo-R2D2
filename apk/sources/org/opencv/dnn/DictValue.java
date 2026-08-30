package org.opencv.dnn;

/* JADX INFO: loaded from: classes.dex */
public class DictValue {
    protected final long nativeObj;

    private static native long DictValue_0(String str);

    private static native long DictValue_1(double d);

    private static native long DictValue_2(int i);

    private static native void delete(long j);

    private static native int getIntValue_0(long j, int i);

    private static native int getIntValue_1(long j);

    private static native double getRealValue_0(long j, int i);

    private static native double getRealValue_1(long j);

    private static native String getStringValue_0(long j, int i);

    private static native String getStringValue_1(long j);

    private static native boolean isInt_0(long j);

    private static native boolean isReal_0(long j);

    private static native boolean isString_0(long j);

    protected DictValue(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public DictValue(String s) {
        this.nativeObj = DictValue_0(s);
    }

    public DictValue(double p) {
        this.nativeObj = DictValue_1(p);
    }

    public DictValue(int i) {
        this.nativeObj = DictValue_2(i);
    }

    public String getStringValue(int idx) {
        String retVal = getStringValue_0(this.nativeObj, idx);
        return retVal;
    }

    public String getStringValue() {
        String retVal = getStringValue_1(this.nativeObj);
        return retVal;
    }

    public boolean isInt() {
        boolean retVal = isInt_0(this.nativeObj);
        return retVal;
    }

    public boolean isReal() {
        boolean retVal = isReal_0(this.nativeObj);
        return retVal;
    }

    public boolean isString() {
        boolean retVal = isString_0(this.nativeObj);
        return retVal;
    }

    public double getRealValue(int idx) {
        double retVal = getRealValue_0(this.nativeObj, idx);
        return retVal;
    }

    public double getRealValue() {
        double retVal = getRealValue_1(this.nativeObj);
        return retVal;
    }

    public int getIntValue(int idx) {
        int retVal = getIntValue_0(this.nativeObj, idx);
        return retVal;
    }

    public int getIntValue() {
        int retVal = getIntValue_1(this.nativeObj);
        return retVal;
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
