package org.opencv.features2d;

import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;

/* JADX INFO: loaded from: classes.dex */
public class BRISK extends Feature2D {
    private static native long create_0(int i, int i2, float f);

    private static native long create_1();

    private static native long create_2(long j, long j2, float f, float f2, long j3);

    private static native long create_3(long j, long j2);

    private static native void delete(long j);

    protected BRISK(long addr) {
        super(addr);
    }

    public static BRISK create(int thresh, int octaves, float patternScale) {
        BRISK retVal = new BRISK(create_0(thresh, octaves, patternScale));
        return retVal;
    }

    public static BRISK create() {
        BRISK retVal = new BRISK(create_1());
        return retVal;
    }

    public static BRISK create(MatOfFloat radiusList, MatOfInt numberList, float dMax, float dMin, MatOfInt indexChange) {
        BRISK retVal = new BRISK(create_2(radiusList.nativeObj, numberList.nativeObj, dMax, dMin, indexChange.nativeObj));
        return retVal;
    }

    public static BRISK create(MatOfFloat radiusList, MatOfInt numberList) {
        BRISK retVal = new BRISK(create_3(radiusList.nativeObj, numberList.nativeObj));
        return retVal;
    }

    @Override // org.opencv.features2d.Feature2D, org.opencv.core.Algorithm
    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
