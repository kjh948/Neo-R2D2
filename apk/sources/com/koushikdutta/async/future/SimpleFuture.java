package com.koushikdutta.async.future;

import com.koushikdutta.async.AsyncSemaphore;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* JADX INFO: loaded from: classes.dex */
public class SimpleFuture<T> extends SimpleCancellable implements DependentFuture<T> {
    FutureCallback<T> callback;
    Exception exception;
    T result;
    boolean silent;
    AsyncSemaphore waiter;

    public SimpleFuture() {
    }

    public SimpleFuture(T value) {
        setComplete(value);
    }

    public SimpleFuture(Exception e) {
        setComplete(e);
    }

    @Override // java.util.concurrent.Future
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    private boolean cancelInternal(boolean silent) {
        FutureCallback<T> callback;
        if (!super.cancel()) {
            return false;
        }
        synchronized (this) {
            this.exception = new CancellationException();
            releaseWaiterLocked();
            callback = handleCompleteLocked();
            this.silent = silent;
        }
        handleCallbackUnlocked(callback);
        return true;
    }

    public boolean cancelSilently() {
        return cancelInternal(true);
    }

    @Override // com.koushikdutta.async.future.SimpleCancellable, com.koushikdutta.async.future.Cancellable
    public boolean cancel() {
        return cancelInternal(this.silent);
    }

    @Override // java.util.concurrent.Future
    public T get() throws ExecutionException, InterruptedException {
        synchronized (this) {
            if (isCancelled() || isDone()) {
                return getResultOrThrow();
            }
            AsyncSemaphore waiter = ensureWaiterLocked();
            waiter.acquire();
            return getResultOrThrow();
        }
    }

    private T getResultOrThrow() throws ExecutionException {
        if (this.exception != null) {
            throw new ExecutionException(this.exception);
        }
        return this.result;
    }

    @Override // java.util.concurrent.Future
    public T get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        synchronized (this) {
            if (isCancelled() || isDone()) {
                return getResultOrThrow();
            }
            AsyncSemaphore waiter = ensureWaiterLocked();
            if (!waiter.tryAcquire(timeout, unit)) {
                throw new TimeoutException();
            }
            return getResultOrThrow();
        }
    }

    @Override // com.koushikdutta.async.future.SimpleCancellable
    public boolean setComplete() {
        return setComplete((Object) null);
    }

    private FutureCallback<T> handleCompleteLocked() {
        FutureCallback<T> callback = this.callback;
        this.callback = null;
        return callback;
    }

    private void handleCallbackUnlocked(FutureCallback<T> callback) {
        if (callback != null && !this.silent) {
            callback.onCompleted(this.exception, this.result);
        }
    }

    void releaseWaiterLocked() {
        if (this.waiter != null) {
            this.waiter.release();
            this.waiter = null;
        }
    }

    AsyncSemaphore ensureWaiterLocked() {
        if (this.waiter == null) {
            this.waiter = new AsyncSemaphore();
        }
        return this.waiter;
    }

    public boolean setComplete(Exception e) {
        return setComplete(e, null);
    }

    public boolean setComplete(T value) {
        return setComplete(null, value);
    }

    public boolean setComplete(Exception e, T value) {
        synchronized (this) {
            if (!super.setComplete()) {
                return false;
            }
            this.result = value;
            this.exception = e;
            releaseWaiterLocked();
            FutureCallback<T> callback = handleCompleteLocked();
            handleCallbackUnlocked(callback);
            return true;
        }
    }

    public FutureCallback<T> getCompletionCallback() {
        return new FutureCallback<T>() { // from class: com.koushikdutta.async.future.SimpleFuture.1
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, T result) {
                SimpleFuture.this.setComplete(e, result);
            }
        };
    }

    public SimpleFuture<T> setComplete(Future<T> future) {
        future.setCallback(getCompletionCallback());
        setParent((Cancellable) future);
        return this;
    }

    public FutureCallback<T> getCallback() {
        return this.callback;
    }

    @Override // com.koushikdutta.async.future.Future
    public SimpleFuture<T> setCallback(FutureCallback<T> callback) {
        FutureCallback<T> callback2;
        synchronized (this) {
            this.callback = callback;
            if (isDone() || isCancelled()) {
                callback2 = handleCompleteLocked();
            } else {
                callback2 = null;
            }
        }
        handleCallbackUnlocked(callback2);
        return this;
    }

    @Override // com.koushikdutta.async.future.Future
    public final <C extends FutureCallback<T>> C then(C callback) {
        if (callback instanceof DependentCancellable) {
            ((DependentCancellable) callback).setParent(this);
        }
        setCallback((FutureCallback) callback);
        return callback;
    }

    @Override // com.koushikdutta.async.future.SimpleCancellable, com.koushikdutta.async.future.DependentCancellable
    public SimpleFuture<T> setParent(Cancellable parent) {
        super.setParent(parent);
        return this;
    }

    @Override // com.koushikdutta.async.future.SimpleCancellable
    public SimpleFuture<T> reset() {
        super.reset();
        this.result = null;
        this.exception = null;
        this.waiter = null;
        this.callback = null;
        this.silent = false;
        return this;
    }

    @Override // com.koushikdutta.async.future.Future
    public Exception tryGetException() {
        return this.exception;
    }

    @Override // com.koushikdutta.async.future.Future
    public T tryGet() {
        return this.result;
    }
}
