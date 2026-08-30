package com.koushikdutta.async.util;

import java.util.Hashtable;

/* JADX INFO: loaded from: classes.dex */
public class UntypedHashtable {
    private Hashtable<String, Object> hash = new Hashtable<>();

    public void put(String key, Object value) {
        this.hash.put(key, value);
    }

    public void remove(String key) {
        this.hash.remove(key);
    }

    public <T> T get(String str, T t) {
        T t2 = (T) get(str);
        return t2 == null ? t : t2;
    }

    public <T> T get(String str) {
        return (T) this.hash.get(str);
    }
}
