package org.opencv.core;

/* JADX INFO: loaded from: classes.dex */
public class CvException extends RuntimeException {
    private static final long serialVersionUID = 1;

    public CvException(String msg) {
        super(msg);
    }

    @Override // java.lang.Throwable
    public String toString() {
        return "CvException [" + super.toString() + "]";
    }
}
