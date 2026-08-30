package com.koushikdutta.async.http.spdy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
final class Util {
    Util() {
    }

    public static void checkOffsetAndCount(long arrayLength, long offset, long count) {
        if ((offset | count) < 0 || offset > arrayLength || arrayLength - offset < count) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public static <T> List<T> immutableList(List<T> list) {
        return Collections.unmodifiableList(new ArrayList(list));
    }

    public static <T> List<T> immutableList(T... elements) {
        return Collections.unmodifiableList(Arrays.asList((Object[]) elements.clone()));
    }
}
