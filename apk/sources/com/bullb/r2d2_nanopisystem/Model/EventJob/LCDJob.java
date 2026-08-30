package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class LCDJob extends EventJob {
    public static final int LCD_CLOSED = 1;
    public static final int LCD_OPEN = 2;
    private int l;
    private int s;

    public LCDJob(int s, int l, int delay) {
        super(Commander.LCD, delay);
        this.s = s;
        this.l = l;
    }

    public int getS() {
        return this.s;
    }

    public int getL() {
        return this.l;
    }
}
