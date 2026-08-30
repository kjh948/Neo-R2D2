package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class ProjectorJob extends EventJob {
    private int mode;

    public ProjectorJob(int mode, int delay) {
        super(Commander.PROJECTOR, delay);
        this.mode = mode;
    }

    public int getMode() {
        return this.mode;
    }
}
