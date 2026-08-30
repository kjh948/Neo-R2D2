package org.opencv.photo;

/* JADX INFO: loaded from: classes.dex */
public class TonemapReinhard extends Tonemap {
    private static native void delete(long j);

    private static native float getColorAdaptation_0(long j);

    private static native float getIntensity_0(long j);

    private static native float getLightAdaptation_0(long j);

    private static native void setColorAdaptation_0(long j, float f);

    private static native void setIntensity_0(long j, float f);

    private static native void setLightAdaptation_0(long j, float f);

    protected TonemapReinhard(long addr) {
        super(addr);
    }

    public float getColorAdaptation() {
        float retVal = getColorAdaptation_0(this.nativeObj);
        return retVal;
    }

    public float getIntensity() {
        float retVal = getIntensity_0(this.nativeObj);
        return retVal;
    }

    public float getLightAdaptation() {
        float retVal = getLightAdaptation_0(this.nativeObj);
        return retVal;
    }

    public void setColorAdaptation(float color_adapt) {
        setColorAdaptation_0(this.nativeObj, color_adapt);
    }

    public void setIntensity(float intensity) {
        setIntensity_0(this.nativeObj, intensity);
    }

    public void setLightAdaptation(float light_adapt) {
        setLightAdaptation_0(this.nativeObj, light_adapt);
    }

    @Override // org.opencv.photo.Tonemap, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
