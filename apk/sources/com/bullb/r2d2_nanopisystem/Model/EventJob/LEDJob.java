package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class LEDJob extends EventJob {
    public static final int MODE_FF1 = 5;
    public static final int MODE_FF2 = 6;
    public static final int MODE_NONE = 0;
    public static final int MODE_OFF = 1;
    public static final int MODE_ON = 2;
    public static final int MODE_SF1 = 3;
    public static final int MODE_SF2 = 4;
    private int b;
    private int g;
    private int r;
    private int y;

    public LEDJob(int r, int b, int y, int g, int delay) {
        super(Commander.LED, delay);
        this.r = r;
        this.b = b;
        this.y = y;
        this.g = g;
    }

    public int getR() {
        return this.r;
    }

    public int getB() {
        return this.b;
    }

    public int getY() {
        return this.y;
    }

    public int getG() {
        return this.g;
    }
}
