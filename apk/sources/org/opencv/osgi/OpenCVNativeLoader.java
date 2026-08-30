package org.opencv.osgi;

import java.util.logging.Level;
import java.util.logging.Logger;

/* JADX INFO: loaded from: classes.dex */
public class OpenCVNativeLoader implements OpenCVInterface {
    public void init() {
        System.loadLibrary("opencv_java3");
        Logger.getLogger("org.opencv.osgi").log(Level.INFO, "Successfully loaded OpenCV native library.");
    }
}
