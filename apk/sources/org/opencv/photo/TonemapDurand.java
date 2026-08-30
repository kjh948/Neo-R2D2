package org.opencv.photo;

/* JADX INFO: loaded from: classes.dex */
public class TonemapDurand extends Tonemap {
    private static native void delete(long j);

    private static native float getContrast_0(long j);

    private static native float getSaturation_0(long j);

    private static native float getSigmaColor_0(long j);

    private static native float getSigmaSpace_0(long j);

    private static native void setContrast_0(long j, float f);

    private static native void setSaturation_0(long j, float f);

    private static native void setSigmaColor_0(long j, float f);

    private static native void setSigmaSpace_0(long j, float f);

    protected TonemapDurand(long addr) {
        super(addr);
    }

    public float getContrast() {
        float retVal = getContrast_0(this.nativeObj);
        return retVal;
    }

    public float getSaturation() {
        float retVal = getSaturation_0(this.nativeObj);
        return retVal;
    }

    public float getSigmaColor() {
        float retVal = getSigmaColor_0(this.nativeObj);
        return retVal;
    }

    public float getSigmaSpace() {
        float retVal = getSigmaSpace_0(this.nativeObj);
        return retVal;
    }

    public void setContrast(float contrast) {
        setContrast_0(this.nativeObj, contrast);
    }

    public void setSaturation(float saturation) {
        setSaturation_0(this.nativeObj, saturation);
    }

    public void setSigmaColor(float sigma_color) {
        setSigmaColor_0(this.nativeObj, sigma_color);
    }

    public void setSigmaSpace(float sigma_space) {
        setSigmaSpace_0(this.nativeObj, sigma_space);
    }

    @Override // org.opencv.photo.Tonemap, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
