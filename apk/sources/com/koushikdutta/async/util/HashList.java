package com.koushikdutta.async.util;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public class HashList<T> {
    Hashtable<String, TaggedList<T>> internal = new Hashtable<>();

    public Set<String> keySet() {
        return this.internal.keySet();
    }

    public synchronized <V> V tag(String str) {
        TaggedList<T> taggedList;
        taggedList = this.internal.get(str);
        return taggedList == null ? null : (V) taggedList.tag();
    }

    public synchronized <V> void tag(String key, V tag) {
        TaggedList<T> list = this.internal.get(key);
        if (list == null) {
            list = new TaggedList<>();
            this.internal.put(key, list);
        }
        list.tag(tag);
    }

    public synchronized ArrayList<T> remove(String key) {
        return this.internal.remove(key);
    }

    public synchronized int size() {
        return this.internal.size();
    }

    public synchronized ArrayList<T> get(String key) {
        return this.internal.get(key);
    }

    /* JADX WARN: Removed duplicated region for block: B:10:0x0010  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public synchronized boolean contains(java.lang.String r3) {
        /*
            r2 = this;
            monitor-enter(r2)
            java.util.ArrayList r0 = r2.get(r3)     // Catch: java.lang.Throwable -> L12
            if (r0 == 0) goto L10
            int r1 = r0.size()     // Catch: java.lang.Throwable -> L12
            if (r1 <= 0) goto L10
            r1 = 1
        Le:
            monitor-exit(r2)
            return r1
        L10:
            r1 = 0
            goto Le
        L12:
            r1 = move-exception
            monitor-exit(r2)
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.koushikdutta.async.util.HashList.contains(java.lang.String):boolean");
    }

    public synchronized void add(String key, T value) {
        ArrayList<T> ret = get(key);
        if (ret == null) {
            TaggedList<T> put = new TaggedList<>();
            ret = put;
            this.internal.put(key, put);
        }
        ret.add(value);
    }

    public synchronized T pop(String key) {
        T tRemove = null;
        synchronized (this) {
            TaggedList<T> values = this.internal.get(key);
            if (values != null && values.size() != 0) {
                tRemove = values.remove(values.size() - 1);
            }
        }
        return tRemove;
    }

    public synchronized boolean removeItem(String key, T value) {
        boolean z = false;
        synchronized (this) {
            TaggedList<T> values = this.internal.get(key);
            if (values != null) {
                values.remove(value);
                if (values.size() == 0) {
                    z = true;
                }
            }
        }
        return z;
    }
}
