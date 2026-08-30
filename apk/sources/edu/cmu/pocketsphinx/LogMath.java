package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class LogMath {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected LogMath(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(LogMath obj) {
        if (obj == null) {
            return 0L;
        }
        return obj.swigCPtr;
    }

    protected void finalize() {
        delete();
    }

    public synchronized void delete() {
        if (this.swigCPtr != 0) {
            if (this.swigCMemOwn) {
                this.swigCMemOwn = false;
                SphinxBaseJNI.delete_LogMath(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    public LogMath() {
        this(SphinxBaseJNI.new_LogMath(), true);
    }

    public double exp(int prob) {
        return SphinxBaseJNI.LogMath_exp(this.swigCPtr, this, prob);
    }
}
