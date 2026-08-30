package com.koushikdutta.async.future;

/* JADX INFO: loaded from: classes.dex */
public abstract class TransformFuture<T, F> extends SimpleFuture<T> implements FutureCallback<F> {
    protected abstract void transform(F f) throws Exception;

    @Override // com.koushikdutta.async.future.FutureCallback
    public void onCompleted(Exception e, F result) {
        if (!isCancelled()) {
            if (e != null) {
                error(e);
                return;
            }
            try {
                transform(result);
            } catch (Exception ex) {
                error(ex);
            }
        }
    }

    protected void error(Exception e) {
        setComplete(e);
    }
}
