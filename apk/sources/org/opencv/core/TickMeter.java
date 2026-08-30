package org.opencv.core;

/* JADX INFO: loaded from: classes.dex */
public class TickMeter {
    protected final long nativeObj;

    private static native long TickMeter_0();

    private static native void delete(long j);

    private static native long getCounter_0(long j);

    private static native double getTimeMicro_0(long j);

    private static native double getTimeMilli_0(long j);

    private static native double getTimeSec_0(long j);

    private static native long getTimeTicks_0(long j);

    private static native void reset_0(long j);

    private static native void start_0(long j);

    private static native void stop_0(long j);

    protected TickMeter(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public TickMeter() {
        this.nativeObj = TickMeter_0();
    }

    public double getTimeMicro() {
        double retVal = getTimeMicro_0(this.nativeObj);
        return retVal;
    }

    public double getTimeMilli() {
        double retVal = getTimeMilli_0(this.nativeObj);
        return retVal;
    }

    public double getTimeSec() {
        double retVal = getTimeSec_0(this.nativeObj);
        return retVal;
    }

    public long getCounter() {
        long retVal = getCounter_0(this.nativeObj);
        return retVal;
    }

    public long getTimeTicks() {
        long retVal = getTimeTicks_0(this.nativeObj);
        return retVal;
    }

    public void reset() {
        reset_0(this.nativeObj);
    }

    public void start() {
        start_0(this.nativeObj);
    }

    public void stop() {
        stop_0(this.nativeObj);
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
