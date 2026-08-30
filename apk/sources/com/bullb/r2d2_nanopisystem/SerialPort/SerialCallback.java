package com.bullb.r2d2_nanopisystem.SerialPort;

/* JADX INFO: loaded from: classes.dex */
public interface SerialCallback {
    void fail(Exception exc);

    void success(String str);
}
