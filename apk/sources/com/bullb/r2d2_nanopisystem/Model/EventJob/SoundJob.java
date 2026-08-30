package com.bullb.r2d2_nanopisystem.Model.EventJob;

import com.bullb.r2d2_nanopisystem.Commander;

/* JADX INFO: loaded from: classes.dex */
public class SoundJob extends EventJob {
    private int soundId;

    public SoundJob(int soundId, int delay) {
        super(Commander.PLAY_SOUND, delay);
        this.soundId = soundId;
    }

    public int getSound() {
        return this.soundId;
    }
}
