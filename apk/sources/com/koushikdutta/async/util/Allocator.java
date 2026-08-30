package com.koushikdutta.async.util;

import com.koushikdutta.async.ByteBufferList;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class Allocator {
    int currentAlloc;
    final int maxAlloc;
    int minAlloc;

    public Allocator(int maxAlloc) {
        this.currentAlloc = 0;
        this.minAlloc = 4096;
        this.maxAlloc = maxAlloc;
    }

    public Allocator() {
        this.currentAlloc = 0;
        this.minAlloc = 4096;
        this.maxAlloc = ByteBufferList.MAX_ITEM_SIZE;
    }

    public ByteBuffer allocate() {
        return allocate(this.currentAlloc);
    }

    public ByteBuffer allocate(int currentAlloc) {
        return ByteBufferList.obtain(Math.min(Math.max(currentAlloc, this.minAlloc), this.maxAlloc));
    }

    public void track(long read) {
        this.currentAlloc = ((int) read) * 2;
    }

    public int getMaxAlloc() {
        return this.maxAlloc;
    }

    public void setCurrentAlloc(int currentAlloc) {
        this.currentAlloc = currentAlloc;
    }

    public int getMinAlloc() {
        return this.minAlloc;
    }

    public Allocator setMinAlloc(int minAlloc) {
        this.minAlloc = minAlloc;
        return this;
    }
}
