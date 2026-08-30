package edu.cmu.pocketsphinx;

import java.util.Iterator;

/* JADX INFO: loaded from: classes.dex */
public class NBestList implements Iterable<NBest> {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected NBestList(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(NBestList obj) {
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
                PocketSphinxJNI.delete_NBestList(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    @Override // java.lang.Iterable
    /* JADX INFO: renamed from: iterator, reason: merged with bridge method [inline-methods] */
    public Iterator<NBest> iterator2() {
        long cPtr = PocketSphinxJNI.NBestList_iterator(this.swigCPtr, this);
        if (cPtr == 0) {
            return null;
        }
        return new NBestIterator(cPtr, true);
    }
}
