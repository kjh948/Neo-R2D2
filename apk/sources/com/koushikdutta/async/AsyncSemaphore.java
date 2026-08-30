package com.koushikdutta.async;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: classes.dex */
public class AsyncSemaphore {
    Semaphore semaphore = new Semaphore(0);

    public void acquire() throws InterruptedException {
        ThreadQueue threadQueue = ThreadQueue.getOrCreateThreadQueue(Thread.currentThread());
        AsyncSemaphore last = threadQueue.waiter;
        threadQueue.waiter = this;
        Semaphore queueSemaphore = threadQueue.queueSemaphore;
        try {
            if (this.semaphore.tryAcquire()) {
                return;
            }
            while (true) {
                Runnable run = threadQueue.remove();
                if (run != null) {
                    run.run();
                } else {
                    int permits = Math.max(1, queueSemaphore.availablePermits());
                    queueSemaphore.acquire(permits);
                    if (this.semaphore.tryAcquire()) {
                        return;
                    }
                }
            }
        } finally {
            threadQueue.waiter = last;
        }
    }

    public boolean tryAcquire(long timeout, TimeUnit timeunit) throws InterruptedException {
        long timeoutMs = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        ThreadQueue threadQueue = ThreadQueue.getOrCreateThreadQueue(Thread.currentThread());
        AsyncSemaphore last = threadQueue.waiter;
        threadQueue.waiter = this;
        Semaphore queueSemaphore = threadQueue.queueSemaphore;
        try {
            if (this.semaphore.tryAcquire()) {
                return true;
            }
            long start = System.currentTimeMillis();
            while (true) {
                Runnable run = threadQueue.remove();
                if (run == null) {
                    int permits = Math.max(1, queueSemaphore.availablePermits());
                    if (!queueSemaphore.tryAcquire(permits, timeoutMs, TimeUnit.MILLISECONDS)) {
                        return false;
                    }
                    if (this.semaphore.tryAcquire()) {
                        return true;
                    }
                    if (System.currentTimeMillis() - start >= timeoutMs) {
                        return false;
                    }
                } else {
                    run.run();
                }
            }
        } finally {
            threadQueue.waiter = last;
        }
    }

    public void release() {
        this.semaphore.release();
        ThreadQueue.release(this);
    }
}
