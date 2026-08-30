package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class MoveHeadDirJob extends EventJob {
    private int dir;

    public MoveHeadDirJob(int dir, int delay) {
        super(Commander.MOVE_HEAD_DIR, delay);
        this.dir = dir;
    }

    public int getDir() {
        return this.dir;
    }
}
