package com.koushikdutta.async.callback;

import com.koushikdutta.async.future.Continuation;

/* JADX INFO: loaded from: classes.dex */
public interface ContinuationCallback {
    void onContinue(Continuation continuation, CompletedCallback completedCallback) throws Exception;
}
