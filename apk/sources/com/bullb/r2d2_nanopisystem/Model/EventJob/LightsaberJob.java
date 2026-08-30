package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class LightsaberJob extends EventJob {
    private int power;

    public LightsaberJob(int power, int delay) {
        super(Commander.LIGHTSABER, delay);
        this.power = power;
    }

    public int getPower() {
        return this.power;
    }
}
