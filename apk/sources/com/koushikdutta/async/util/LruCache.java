package com.koushikdutta.async.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class LruCache<K, V> {
    private int createCount;
    private int evictionCount;
    private int hitCount;
    private final LinkedHashMap<K, V> map;
    private long maxSize;
    private int missCount;
    private int putCount;
    private long size;

    public LruCache(long maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<>(0, 0.75f, true);
    }

    public final V get(K k) {
        V vPut;
        if (k == null) {
            throw new NullPointerException("key == null");
        }
        synchronized (this) {
            V v = this.map.get(k);
            if (v != null) {
                this.hitCount++;
                return v;
            }
            this.missCount++;
            V vCreate = create(k);
            if (vCreate == null) {
                return null;
            }
            synchronized (this) {
                this.createCount++;
                vPut = this.map.put(k, vCreate);
                if (vPut != null) {
                    this.map.put(k, vPut);
                } else {
                    this.size += safeSizeOf(k, vCreate);
                }
            }
            if (vPut != null) {
                entryRemoved(false, k, vCreate, vPut);
                return vPut;
            }
            trimToSize(this.maxSize);
            return vCreate;
        }
    }

    public final V put(K key, V value) {
        V previous;
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }
        synchronized (this) {
            this.putCount++;
            this.size += safeSizeOf(key, value);
            previous = this.map.put(key, value);
            if (previous != null) {
                this.size -= safeSizeOf(key, previous);
            }
        }
        if (previous != null) {
            entryRemoved(false, key, previous, value);
        }
        trimToSize(this.maxSize);
        return previous;
    }

    /* JADX WARN: Code restructure failed: missing block: B:11:0x0037, code lost:
    
        throw new java.lang.IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private void trimToSize(long r12) {
        /*
            r11 = this;
            r8 = 0
        L2:
            monitor-enter(r11)
            long r4 = r11.size     // Catch: java.lang.Throwable -> L38
            int r3 = (r4 > r8 ? 1 : (r4 == r8 ? 0 : -1))
            if (r3 < 0) goto L17
            java.util.LinkedHashMap<K, V> r3 = r11.map     // Catch: java.lang.Throwable -> L38
            boolean r3 = r3.isEmpty()     // Catch: java.lang.Throwable -> L38
            if (r3 == 0) goto L3b
            long r4 = r11.size     // Catch: java.lang.Throwable -> L38
            int r3 = (r4 > r8 ? 1 : (r4 == r8 ? 0 : -1))
            if (r3 == 0) goto L3b
        L17:
            java.lang.IllegalStateException r3 = new java.lang.IllegalStateException     // Catch: java.lang.Throwable -> L38
            java.lang.StringBuilder r4 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L38
            r4.<init>()     // Catch: java.lang.Throwable -> L38
            java.lang.Class r5 = r11.getClass()     // Catch: java.lang.Throwable -> L38
            java.lang.String r5 = r5.getName()     // Catch: java.lang.Throwable -> L38
            java.lang.StringBuilder r4 = r4.append(r5)     // Catch: java.lang.Throwable -> L38
            java.lang.String r5 = ".sizeOf() is reporting inconsistent results!"
            java.lang.StringBuilder r4 = r4.append(r5)     // Catch: java.lang.Throwable -> L38
            java.lang.String r4 = r4.toString()     // Catch: java.lang.Throwable -> L38
            r3.<init>(r4)     // Catch: java.lang.Throwable -> L38
            throw r3     // Catch: java.lang.Throwable -> L38
        L38:
            r3 = move-exception
            monitor-exit(r11)     // Catch: java.lang.Throwable -> L38
            throw r3
        L3b:
            long r4 = r11.size     // Catch: java.lang.Throwable -> L38
            int r3 = (r4 > r12 ? 1 : (r4 == r12 ? 0 : -1))
            if (r3 <= 0) goto L49
            java.util.LinkedHashMap<K, V> r3 = r11.map     // Catch: java.lang.Throwable -> L38
            boolean r3 = r3.isEmpty()     // Catch: java.lang.Throwable -> L38
            if (r3 == 0) goto L4b
        L49:
            monitor-exit(r11)     // Catch: java.lang.Throwable -> L38
            return
        L4b:
            java.util.LinkedHashMap<K, V> r3 = r11.map     // Catch: java.lang.Throwable -> L38
            java.util.Set r3 = r3.entrySet()     // Catch: java.lang.Throwable -> L38
            java.util.Iterator r3 = r3.iterator()     // Catch: java.lang.Throwable -> L38
            java.lang.Object r1 = r3.next()     // Catch: java.lang.Throwable -> L38
            java.util.Map$Entry r1 = (java.util.Map.Entry) r1     // Catch: java.lang.Throwable -> L38
            java.lang.Object r0 = r1.getKey()     // Catch: java.lang.Throwable -> L38
            java.lang.Object r2 = r1.getValue()     // Catch: java.lang.Throwable -> L38
            java.util.LinkedHashMap<K, V> r3 = r11.map     // Catch: java.lang.Throwable -> L38
            r3.remove(r0)     // Catch: java.lang.Throwable -> L38
            long r4 = r11.size     // Catch: java.lang.Throwable -> L38
            long r6 = r11.safeSizeOf(r0, r2)     // Catch: java.lang.Throwable -> L38
            long r4 = r4 - r6
            r11.size = r4     // Catch: java.lang.Throwable -> L38
            int r3 = r11.evictionCount     // Catch: java.lang.Throwable -> L38
            int r3 = r3 + 1
            r11.evictionCount = r3     // Catch: java.lang.Throwable -> L38
            monitor-exit(r11)     // Catch: java.lang.Throwable -> L38
            r3 = 1
            r4 = 0
            r11.entryRemoved(r3, r0, r2, r4)
            goto L2
        */
        throw new UnsupportedOperationException("Method not decompiled: com.koushikdutta.async.util.LruCache.trimToSize(long):void");
    }

    public final V remove(K key) {
        V previous;
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        synchronized (this) {
            previous = this.map.remove(key);
            if (previous != null) {
                this.size -= safeSizeOf(key, previous);
            }
        }
        if (previous != null) {
            entryRemoved(false, key, previous, null);
        }
        return previous;
    }

    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
    }

    protected V create(K key) {
        return null;
    }

    private long safeSizeOf(K key, V value) {
        long result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }

    protected long sizeOf(K key, V value) {
        return 1L;
    }

    public final void evictAll() {
        trimToSize(-1L);
    }

    public final synchronized long size() {
        return this.size;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public final synchronized long maxSize() {
        return this.maxSize;
    }

    public final synchronized int hitCount() {
        return this.hitCount;
    }

    public final synchronized int missCount() {
        return this.missCount;
    }

    public final synchronized int createCount() {
        return this.createCount;
    }

    public final synchronized int putCount() {
        return this.putCount;
    }

    public final synchronized int evictionCount() {
        return this.evictionCount;
    }

    public final synchronized Map<K, V> snapshot() {
        return new LinkedHashMap(this.map);
    }

    public final synchronized String toString() {
        String str;
        synchronized (this) {
            int accesses = this.hitCount + this.missCount;
            int hitPercent = accesses != 0 ? (this.hitCount * 100) / accesses : 0;
            str = String.format(Locale.ENGLISH, "LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]", Long.valueOf(this.maxSize), Integer.valueOf(this.hitCount), Integer.valueOf(this.missCount), Integer.valueOf(hitPercent));
        }
        return str;
    }
}
