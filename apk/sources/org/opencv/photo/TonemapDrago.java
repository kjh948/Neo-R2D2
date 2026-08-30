package org.opencv.photo;

/* JADX INFO: loaded from: classes.dex */
public class TonemapDrago extends Tonemap {
    private static native void delete(long j);

    private static native float getBias_0(long j);

    private static native float getSaturation_0(long j);

    private static native void setBias_0(long j, float f);

    private static native void setSaturation_0(long j, float f);

    protected TonemapDrago(long addr) {
        super(addr);
    }

    public float getBias() {
        float retVal = getBias_0(this.nativeObj);
        return retVal;
    }

    public float getSaturation() {
        float retVal = getSaturation_0(this.nativeObj);
        return retVal;
    }

    public void setBias(float bias) {
        setBias_0(this.nativeObj, bias);
    }

    public void setSaturation(float saturation) {
        setSaturation_0(this.nativeObj, saturation);
    }

    @Override // org.opencv.photo.Tonemap, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
