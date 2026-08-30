package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class JsgfRule {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected JsgfRule(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(JsgfRule obj) {
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
                SphinxBaseJNI.delete_JsgfRule(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    public JsgfRule() {
        this(SphinxBaseJNI.new_JsgfRule(), true);
    }

    public static JsgfRule fromIter(SWIGTYPE_p_void itor) {
        long cPtr = SphinxBaseJNI.JsgfRule_fromIter(SWIGTYPE_p_void.getCPtr(itor));
        if (cPtr == 0) {
            return null;
        }
        return new JsgfRule(cPtr, false);
    }

    public String getName() {
        return SphinxBaseJNI.JsgfRule_getName(this.swigCPtr, this);
    }

    public boolean isPublic() {
        return SphinxBaseJNI.JsgfRule_isPublic(this.swigCPtr, this);
    }
}
