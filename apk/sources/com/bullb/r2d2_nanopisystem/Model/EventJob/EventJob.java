package com.bullb.r2d2_nanopisystem.Model.EventJob;

/* JADX INFO: loaded from: classes.dex */
public class EventJob {
    private String command;
    private int delay;

    public EventJob(String command, int delay) {
        this.delay = 0;
        this.command = command;
        this.delay = delay;
    }

    public int getDelay() {
        return this.delay;
    }

    public String getCommand() {
        return this.command;
    }
}
