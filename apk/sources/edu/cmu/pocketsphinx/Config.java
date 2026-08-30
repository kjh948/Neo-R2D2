package edu.cmu.pocketsphinx;

/* JADX INFO: loaded from: classes.dex */
public class Config {
    protected transient boolean swigCMemOwn;
    private transient long swigCPtr;

    protected Config(long cPtr, boolean cMemoryOwn) {
        this.swigCMemOwn = cMemoryOwn;
        this.swigCPtr = cPtr;
    }

    protected static long getCPtr(Config obj) {
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
                SphinxBaseJNI.delete_Config(this.swigCPtr);
            }
            this.swigCPtr = 0L;
        }
    }

    public void setBoolean(String key, boolean val) {
        SphinxBaseJNI.Config_setBoolean(this.swigCPtr, this, key, val);
    }

    public void setInt(String key, int val) {
        SphinxBaseJNI.Config_setInt(this.swigCPtr, this, key, val);
    }

    public void setFloat(String key, double val) {
        SphinxBaseJNI.Config_setFloat(this.swigCPtr, this, key, val);
    }

    public void setString(String key, String val) {
        SphinxBaseJNI.Config_setString(this.swigCPtr, this, key, val);
    }

    public void setStringExtra(String key, String val) {
        SphinxBaseJNI.Config_setStringExtra(this.swigCPtr, this, key, val);
    }

    public boolean exists(String key) {
        return SphinxBaseJNI.Config_exists(this.swigCPtr, this, key);
    }

    public boolean getBoolean(String key) {
        return SphinxBaseJNI.Config_getBoolean(this.swigCPtr, this, key);
    }

    public int getInt(String key) {
        return SphinxBaseJNI.Config_getInt(this.swigCPtr, this, key);
    }

    public double getFloat(String key) {
        return SphinxBaseJNI.Config_getFloat(this.swigCPtr, this, key);
    }

    public String getString(String key) {
        return SphinxBaseJNI.Config_getString(this.swigCPtr, this, key);
    }
}
