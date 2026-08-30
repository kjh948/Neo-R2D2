package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class Segment {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected Segment(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(Segment obj) {
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
                PocketSphinxJNI.delete_Segment(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    public void setWord(String value) {
        PocketSphinxJNI.Segment_word_set(this.swigCPtr, this, value);
    }

    public String getWord() {
        return PocketSphinxJNI.Segment_word_get(this.swigCPtr, this);
    }

    public void setAscore(int value) {
        PocketSphinxJNI.Segment_ascore_set(this.swigCPtr, this, value);
    }

    public int getAscore() {
        return PocketSphinxJNI.Segment_ascore_get(this.swigCPtr, this);
    }

    public void setLscore(int value) {
        PocketSphinxJNI.Segment_lscore_set(this.swigCPtr, this, value);
    }

    public int getLscore() {
        return PocketSphinxJNI.Segment_lscore_get(this.swigCPtr, this);
    }

    public void setLback(int value) {
        PocketSphinxJNI.Segment_lback_set(this.swigCPtr, this, value);
    }

    public int getLback() {
        return PocketSphinxJNI.Segment_lback_get(this.swigCPtr, this);
    }

    public void setProb(int value) {
        PocketSphinxJNI.Segment_prob_set(this.swigCPtr, this, value);
    }

    public int getProb() {
        return PocketSphinxJNI.Segment_prob_get(this.swigCPtr, this);
    }

    public void setStartFrame(int value) {
        PocketSphinxJNI.Segment_startFrame_set(this.swigCPtr, this, value);
    }

    public int getStartFrame() {
        return PocketSphinxJNI.Segment_startFrame_get(this.swigCPtr, this);
    }

    public void setEndFrame(int value) {
        PocketSphinxJNI.Segment_endFrame_set(this.swigCPtr, this, value);
    }

    public int getEndFrame() {
        return PocketSphinxJNI.Segment_endFrame_get(this.swigCPtr, this);
    }

    public static Segment fromIter(SWIGTYPE_p_void itor) {
        long cPtr = PocketSphinxJNI.Segment_fromIter(SWIGTYPE_p_void.getCPtr(itor));
        if (cPtr == 0) {
            return null;
        }
        return new Segment(cPtr, false);
    }

    public Segment() {
        this(PocketSphinxJNI.new_segment(), true);
    }
}
