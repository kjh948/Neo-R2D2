package com.koushikdutta.async.future;

/* JADX INFO: loaded from: classes.dex */
public interface DependentCancellable extends Cancellable {
    DependentCancellable setParent(Cancellable cancellable);
}
