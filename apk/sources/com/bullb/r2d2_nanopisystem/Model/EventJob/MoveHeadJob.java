package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class MoveHeadJob extends EventJob {
    private int angle;

    public MoveHeadJob(int angle, int delay) {
        super(Commander.MOVE_HEAD_ANGLE, delay);
        this.angle = angle;
    }

    public int getAngle() {
        return this.angle;
    }
}
