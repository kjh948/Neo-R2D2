package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class PatrolJob extends EventJob {
    private boolean enable;

    public PatrolJob(boolean enable, int delay) {
        super(Commander.ARM, delay);
        this.enable = enable;
    }

    public boolean isEnable() {
        return this.enable;
    }
}
