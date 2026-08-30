package org.opencv.videoio;

import org.opencv.core.Mat;

/* JADX INFO: loaded from: classes.dex */
public class VideoCapture {
    protected final long nativeObj;

    private static native long VideoCapture_0(String str, int i);

    private static native long VideoCapture_1(String str);

    private static native long VideoCapture_2(int i);

    private static native long VideoCapture_3();

    private static native void delete(long j);

    private static native double get_0(long j, int i);

    private static native boolean grab_0(long j);

    private static native boolean isOpened_0(long j);

    private static native boolean open_0(long j, String str, int i);

    private static native boolean open_1(long j, String str);

    private static native boolean open_2(long j, int i, int i2);

    private static native boolean open_3(long j, int i);

    private static native boolean read_0(long j, long j2);

    private static native void release_0(long j);

    private static native boolean retrieve_0(long j, long j2, int i);

    private static native boolean retrieve_1(long j, long j2);

    private static native boolean set_0(long j, int i, double d);

    protected VideoCapture(long addr) {
        this.nativeObj = addr;
    }

    public long getNativeObjAddr() {
        return this.nativeObj;
    }

    public VideoCapture(String filename, int apiPreference) {
        this.nativeObj = VideoCapture_0(filename, apiPreference);
    }

    public VideoCapture(String filename) {
        this.nativeObj = VideoCapture_1(filename);
    }

    public VideoCapture(int index) {
        this.nativeObj = VideoCapture_2(index);
    }

    public VideoCapture() {
        this.nativeObj = VideoCapture_3();
    }

    public boolean grab() {
        boolean retVal = grab_0(this.nativeObj);
        return retVal;
    }

    public boolean isOpened() {
        boolean retVal = isOpened_0(this.nativeObj);
        return retVal;
    }

    public boolean open(String filename, int apiPreference) {
        boolean retVal = open_0(this.nativeObj, filename, apiPreference);
        return retVal;
    }

    public boolean open(String filename) {
        boolean retVal = open_1(this.nativeObj, filename);
        return retVal;
    }

    public boolean open(int cameraNum, int apiPreference) {
        boolean retVal = open_2(this.nativeObj, cameraNum, apiPreference);
        return retVal;
    }

    public boolean open(int index) {
        boolean retVal = open_3(this.nativeObj, index);
        return retVal;
    }

    public boolean read(Mat image) {
        boolean retVal = read_0(this.nativeObj, image.nativeObj);
        return retVal;
    }

    public boolean retrieve(Mat image, int flag) {
        boolean retVal = retrieve_0(this.nativeObj, image.nativeObj, flag);
        return retVal;
    }

    public boolean retrieve(Mat image) {
        boolean retVal = retrieve_1(this.nativeObj, image.nativeObj);
        return retVal;
    }

    public boolean set(int propId, double value) {
        boolean retVal = set_0(this.nativeObj, propId, value);
        return retVal;
    }

    public double get(int propId) {
        double retVal = get_0(this.nativeObj, propId);
        return retVal;
    }

    public void release() {
        release_0(this.nativeObj);
    }

    protected void finalize() throws Throwable {
        delete(this.nativeObj);
    }
}
