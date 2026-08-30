package org.opencv.photo;

/* JADX INFO: loaded from: classes.dex */
public class TonemapMantiuk extends Tonemap {
    private static native void delete(long j);

    private static native float getSaturation_0(long j);

    private static native float getScale_0(long j);

    private static native void setSaturation_0(long j, float f);

    private static native void setScale_0(long j, float f);

    protected TonemapMantiuk(long addr) {
        super(addr);
    }

    public float getSaturation() {
        float retVal = getSaturation_0(this.nativeObj);
        return retVal;
    }

    public float getScale() {
        float retVal = getScale_0(this.nativeObj);
        return retVal;
    }

    public void setSaturation(float saturation) {
        setSaturation_0(this.nativeObj, saturation);
    }

    public void setScale(float scale) {
        setScale_0(this.nativeObj, scale);
    }

    @Override // org.opencv.photo.Tonemap, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
