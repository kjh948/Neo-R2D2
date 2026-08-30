package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class MoveJob extends EventJob {
    private int angle;
    private int power;

    public MoveJob(int power, int angle, int delay) {
        super(Commander.MOVE, delay);
        this.power = power;
        this.angle = angle;
    }

    public int getPower() {
        return this.power;
    }

    public int getAngle() {
        return this.angle;
    }
}
