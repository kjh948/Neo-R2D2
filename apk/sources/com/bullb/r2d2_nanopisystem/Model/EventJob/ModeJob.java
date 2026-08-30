package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class ModeJob extends EventJob {
    public static final int MODE_CIRCLE = 12;
    public static final int MODE_DANCE = 10;
    public static final int MODE_FLASH_BACK_LCD = 14;
    public static final int MODE_FLASH_FRONT_LCD = 13;
    public static final int MODE_GO_FORWARD = 5;
    public static final int MODE_HAND_JOB = 16;
    public static final int MODE_LIGHTSABER = 6;
    public static final int MODE_LONG_LCD = 18;
    public static final int MODE_NOT_RECOGNIZE = 8;
    public static final int MODE_PROJECTOR_1 = 19;
    public static final int MODE_PROJECTOR_2 = 20;
    public static final int MODE_SHAKE_HEAD = 15;
    public static final int MODE_SHORT_LCD = 17;
    public static final int MODE_STOP = 0;
    public static final int MODE_TURN_AROUND = 2;
    public static final int MODE_TURN_LEFT = 3;
    public static final int MODE_TURN_RIGHT = 4;
    public static final int MODE_WAKE = 1;
    public static final int MODE_WHO_ARE_YOU = 7;
    public static final int PATROL = 9;
    public static int[] prohabittedModeWhileCharging = {1, 2, 3, 4, 5, 10, 12, 15, 9};
    private int mode;

    public ModeJob(int mode, int delay) {
        super(Commander.MODE, delay);
        this.mode = mode;
    }

    public int getMode() {
        return this.mode;
    }
}
