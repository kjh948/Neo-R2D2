package com.koushikdutta.async.callback;

/* JADX INFO: loaded from: classes.dex */
public interface CompletedCallback {
    void onCompleted(Exception exc);

    public static class NullCompletedCallback implements CompletedCallback {
        @Override // com.koushikdutta.async.callback.CompletedCallback
        public void onCompleted(Exception ex) {
        }
    }
}
