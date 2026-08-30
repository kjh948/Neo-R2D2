package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class Feature {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected Feature(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(Feature obj) {
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
                SphinxBaseJNI.delete_Feature(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    public Feature() {
        this(SphinxBaseJNI.new_feature(), true);
    }
}
