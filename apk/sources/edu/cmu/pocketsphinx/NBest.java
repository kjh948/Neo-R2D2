package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class NBest {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected NBest(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(NBest obj) {
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
                PocketSphinxJNI.delete_NBest(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    public void setHypstr(String value) {
        PocketSphinxJNI.NBest_hypstr_set(this.swigCPtr, this, value);
    }

    public String getHypstr() {
        return PocketSphinxJNI.NBest_hypstr_get(this.swigCPtr, this);
    }

    public void setScore(int value) {
        PocketSphinxJNI.NBest_score_set(this.swigCPtr, this, value);
    }

    public int getScore() {
        return PocketSphinxJNI.NBest_score_get(this.swigCPtr, this);
    }

    public static NBest fromIter(SWIGTYPE_p_void itor) {
        long cPtr = PocketSphinxJNI.NBest_fromIter(SWIGTYPE_p_void.getCPtr(itor));
        if (cPtr == 0) {
            return null;
        }
        return new NBest(cPtr, false);
    }

    public Hypothesis hyp() {
        long cPtr = PocketSphinxJNI.NBest_hyp(this.swigCPtr, this);
        if (cPtr == 0) {
            return null;
        }
        return new Hypothesis(cPtr, true);
    }

    public NBest() {
        this(PocketSphinxJNI.new_nBest(), true);
    }
}
