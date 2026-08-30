package com.koushikdutta.async;

import android.support.v7.widget.ActivityChooserView;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: classes.dex */
public class SelectorWrapper {
    boolean isWaking;
    private Selector selector;
    Semaphore semaphore = new Semaphore(0);

    public Selector getSelector() {
        return this.selector;
    }

    public SelectorWrapper(Selector selector) {
        this.selector = selector;
    }

    public int selectNow() throws IOException {
        return this.selector.selectNow();
    }

    public void select() throws IOException {
        select(0L);
    }

    public void select(long timeout) throws IOException {
        try {
            this.semaphore.drainPermits();
            this.selector.select(timeout);
        } finally {
            this.semaphore.release(ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
        }
    }

    public Set<SelectionKey> keys() {
        return this.selector.keys();
    }

    public Set<SelectionKey> selectedKeys() {
        return this.selector.selectedKeys();
    }

    public void close() throws IOException {
        this.selector.close();
    }

    public boolean isOpen() {
        return this.selector.isOpen();
    }

    public void wakeupOnce() {
        boolean selecting = this.semaphore.tryAcquire() ? false : true;
        this.selector.wakeup();
        if (!selecting) {
            synchronized (this) {
                if (!this.isWaking) {
                    this.isWaking = true;
                    int i = 0;
                    while (true) {
                        if (i < 100) {
                            try {
                                try {
                                    if (this.semaphore.tryAcquire(10L, TimeUnit.MILLISECONDS)) {
                                        synchronized (this) {
                                            this.isWaking = false;
                                        }
                                        break;
                                    }
                                } catch (InterruptedException e) {
                                }
                                this.selector.wakeup();
                                i++;
                            } catch (Throwable th) {
                                synchronized (this) {
                                    this.isWaking = false;
                                    throw th;
                                }
                            }
                        } else {
                            synchronized (this) {
                                this.isWaking = false;
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
}
