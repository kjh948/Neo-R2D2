package com.koushikdutta.async.http.cache;

/* JADX INFO: loaded from: classes.dex */
final class Objects {
    private Objects() {
    }

    public static boolean equal(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    public static int hashCode(Object o) {
        if (o == null) {
            return 0;
        }
        return o.hashCode();
    }
}
