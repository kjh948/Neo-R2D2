package com.bullb.r2d2_nanopisystem.FaceDetection;

import org.opencv.core.Rect;

/* JADX INFO: loaded from: classes.dex */
public class Face {
    private int faceId;
    private long firstExistTime;
    private long lastExistTime;
    private Rect rect;

    public Face(long lastExistTime, Rect rect, int faceId) {
        this.lastExistTime = lastExistTime;
        this.firstExistTime = lastExistTime;
        this.rect = rect;
        this.faceId = faceId;
    }

    public long getLastExistTime() {
        return this.lastExistTime;
    }

    public Rect getRect() {
        return this.rect;
    }

    public int getFaceId() {
        return this.faceId;
    }

    public void setLastExistTime(long lastExistTime) {
        this.lastExistTime = lastExistTime;
    }

    public void setRect(Rect rect) {
        this.rect = rect;
    }

    public long getFirstExistTime() {
        return this.firstExistTime;
    }
}
