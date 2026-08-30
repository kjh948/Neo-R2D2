package com.koushikdutta.async.future;

/* JADX INFO: loaded from: classes.dex */
public class SimpleCancellable implements DependentCancellable {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static final Cancellable COMPLETED;
    boolean cancelled;
    boolean complete;
    private Cancellable parent;

    static {
        $assertionsDisabled = !SimpleCancellable.class.desiredAssertionStatus();
        COMPLETED = new SimpleCancellable() { // from class: com.koushikdutta.async.future.SimpleCancellable.1
            {
                setComplete();
            }

            @Override // com.koushikdutta.async.future.SimpleCancellable, com.koushikdutta.async.future.DependentCancellable
            public /* bridge */ /* synthetic */ DependentCancellable setParent(Cancellable cancellable) {
                return super.setParent(cancellable);
            }
        };
    }

    @Override // com.koushikdutta.async.future.Cancellable
    public boolean isDone() {
        return this.complete;
    }

    protected void cancelCleanup() {
    }

    protected void cleanup() {
    }

    protected void completeCleanup() {
    }

    public boolean setComplete() {
        boolean z = true;
        synchronized (this) {
            if (this.cancelled) {
                z = false;
            } else if (this.complete) {
                if (!$assertionsDisabled) {
                    throw new AssertionError();
                }
            } else {
                this.complete = true;
                this.parent = null;
                completeCleanup();
                cleanup();
            }
        }
        return z;
    }

    @Override // com.koushikdutta.async.future.Cancellable
    public boolean cancel() {
        boolean z = true;
        synchronized (this) {
            if (this.complete) {
                z = false;
            } else if (!this.cancelled) {
                this.cancelled = true;
                Cancellable parent = this.parent;
                this.parent = null;
                if (parent != null) {
                    parent.cancel();
                }
                cancelCleanup();
                cleanup();
            }
        }
        return z;
    }

    @Override // com.koushikdutta.async.future.DependentCancellable
    public SimpleCancellable setParent(Cancellable parent) {
        synchronized (this) {
            if (!isDone()) {
                this.parent = parent;
            }
        }
        return this;
    }

    @Override // com.koushikdutta.async.future.Cancellable
    public boolean isCancelled() {
        boolean z;
        synchronized (this) {
            z = this.cancelled || (this.parent != null && this.parent.isCancelled());
        }
        return z;
    }

    public Cancellable reset() {
        cancel();
        this.complete = false;
        this.cancelled = false;
        return this;
    }
}
