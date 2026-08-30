package org.opencv.ml;

/* JADX INFO: loaded from: classes.dex */
public class Boost extends DTrees {
    public static final int DISCRETE = 0;
    public static final int GENTLE = 3;
    public static final int LOGIT = 2;
    public static final int REAL = 1;

    private static native long create_0();

    private static native void delete(long j);

    private static native int getBoostType_0(long j);

    private static native int getWeakCount_0(long j);

    private static native double getWeightTrimRate_0(long j);

    private static native long load_0(String str, String str2);

    private static native long load_1(String str);

    private static native void setBoostType_0(long j, int i);

    private static native void setWeakCount_0(long j, int i);

    private static native void setWeightTrimRate_0(long j, double d);

    protected Boost(long addr) {
        super(addr);
    }

    public static Boost create() {
        Boost retVal = new Boost(create_0());
        return retVal;
    }

    public static Boost load(String filepath, String nodeName) {
        Boost retVal = new Boost(load_0(filepath, nodeName));
        return retVal;
    }

    public static Boost load(String filepath) {
        Boost retVal = new Boost(load_1(filepath));
        return retVal;
    }

    public double getWeightTrimRate() {
        double retVal = getWeightTrimRate_0(this.nativeObj);
        return retVal;
    }

    public int getBoostType() {
        int retVal = getBoostType_0(this.nativeObj);
        return retVal;
    }

    public int getWeakCount() {
        int retVal = getWeakCount_0(this.nativeObj);
        return retVal;
    }

    public void setBoostType(int val) {
        setBoostType_0(this.nativeObj, val);
    }

    public void setWeakCount(int val) {
        setWeakCount_0(this.nativeObj, val);
    }

    public void setWeightTrimRate(double val) {
        setWeightTrimRate_0(this.nativeObj, val);
    }

    @Override // org.opencv.ml.DTrees, org.opencv.ml.StatModel, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
