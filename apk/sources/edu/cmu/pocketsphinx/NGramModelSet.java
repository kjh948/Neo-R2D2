package edu.cmu.pocketsphinx;

import java.util.Iterator;

/* JADX INFO: loaded from: classes.dex */
public class NGramModelSet implements Iterable<NGramModel> {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected NGramModelSet(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(NGramModelSet obj) {
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
                SphinxBaseJNI.delete_NGramModelSet(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    @Override // java.lang.Iterable
    /* JADX INFO: renamed from: iterator, reason: merged with bridge method [inline-methods] */
    public Iterator<NGramModel> iterator2() {
        long cPtr = SphinxBaseJNI.NGramModelSet_iterator(this.swigCPtr, this);
        if (cPtr == 0) {
            return null;
        }
        return new NGramModelSetIterator(cPtr, true);
    }

    public NGramModelSet(Config config, LogMath logmath, String path) {
        this(SphinxBaseJNI.new_NGramModelSet(Config.getCPtr(config), config, LogMath.getCPtr(logmath), logmath, path), true);
    }

    public int count() {
        return SphinxBaseJNI.NGramModelSet_count(this.swigCPtr, this);
    }

    public NGramModel add(NGramModel model, String name, float weight, boolean reuse_widmap) {
        long cPtr = SphinxBaseJNI.NGramModelSet_add(this.swigCPtr, this, NGramModel.getCPtr(model), model, name, weight, reuse_widmap);
        if (cPtr == 0) {
            return null;
        }
        return new NGramModel(cPtr, false);
    }

    public NGramModel select(String name) {
        long cPtr = SphinxBaseJNI.NGramModelSet_select(this.swigCPtr, this, name);
        if (cPtr == 0) {
            return null;
        }
        return new NGramModel(cPtr, false);
    }

    public NGramModel lookup(String name) {
        long cPtr = SphinxBaseJNI.NGramModelSet_lookup(this.swigCPtr, this, name);
        if (cPtr == 0) {
            return null;
        }
        return new NGramModel(cPtr, false);
    }

    public String current() {
        return SphinxBaseJNI.NGramModelSet_current(this.swigCPtr, this);
    }
}
