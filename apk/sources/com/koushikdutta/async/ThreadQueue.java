package com.koushikdutta.async;

import java.util.LinkedList;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

/* JADX INFO: loaded from: classes.dex */
public class ThreadQueue extends LinkedList<Runnable> {
    private static final WeakHashMap<Thread, ThreadQueue> mThreadQueues = new WeakHashMap<>();
    Semaphore queueSemaphore = new Semaphore(0);
    AsyncSemaphore waiter;

    static ThreadQueue getOrCreateThreadQueue(Thread thread) {
        ThreadQueue queue;
        synchronized (mThreadQueues) {
            queue = mThreadQueues.get(thread);
            if (queue == null) {
                queue = new ThreadQueue();
                mThreadQueues.put(thread, queue);
            }
        }
        return queue;
    }

    static void release(AsyncSemaphore semaphore) {
        synchronized (mThreadQueues) {
            for (ThreadQueue threadQueue : mThreadQueues.values()) {
                if (threadQueue.waiter == semaphore) {
                    threadQueue.queueSemaphore.release();
                }
            }
        }
    }

    @Override // java.util.LinkedList, java.util.AbstractList, java.util.AbstractCollection, java.util.Collection, java.util.List, java.util.Deque, java.util.Queue
    public boolean add(Runnable object) {
        boolean zAdd;
        synchronized (this) {
            zAdd = super.add(object);
        }
        return zAdd;
    }

    @Override // java.util.LinkedList, java.util.AbstractCollection, java.util.Collection, java.util.List, java.util.Deque
    public boolean remove(Object object) {
        boolean zRemove;
        synchronized (this) {
            zRemove = super.remove(object);
        }
        return zRemove;
    }

    @Override // java.util.LinkedList, java.util.Deque, java.util.Queue
    public Runnable remove() {
        Runnable runnable;
        synchronized (this) {
            runnable = isEmpty() ? null : (Runnable) super.remove();
        }
        return runnable;
    }
}
