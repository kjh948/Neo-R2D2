package com.koushikdutta.async.future;

import java.util.concurrent.ExecutorService;

/* JADX INFO: loaded from: classes.dex */
public class FutureThread<T> extends SimpleFuture<T> {
    public FutureThread(FutureRunnable<T> runnable) {
        this(runnable, "FutureThread");
    }

    public FutureThread(ExecutorService pool, final FutureRunnable<T> runnable) {
        pool.submit(new Runnable() { // from class: com.koushikdutta.async.future.FutureThread.1
            /* JADX WARN: Multi-variable type inference failed */
            @Override // java.lang.Runnable
            public void run() {
                try {
                    FutureThread.this.setComplete(runnable.run());
                } catch (Exception e) {
                    FutureThread.this.setComplete(e);
                }
            }
        });
    }

    public FutureThread(final FutureRunnable<T> runnable, String name) {
        new Thread(new Runnable() { // from class: com.koushikdutta.async.future.FutureThread.2
            /* JADX WARN: Multi-variable type inference failed */
            @Override // java.lang.Runnable
            public void run() {
                try {
                    FutureThread.this.setComplete(runnable.run());
                } catch (Exception e) {
                    FutureThread.this.setComplete(e);
                }
            }
        }, name).start();
    }
}
