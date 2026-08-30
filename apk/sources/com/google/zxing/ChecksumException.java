package com.google.zxing;

/* JADX INFO: loaded from: classes.dex */
public final class ChecksumException extends ReaderException {
    private static final ChecksumException INSTANCE = new ChecksumException();

    static {
        INSTANCE.setStackTrace(NO_TRACE);
    }

    private ChecksumException() {
    }

    private ChecksumException(Throwable cause) {
        super(cause);
    }

    public static ChecksumException getChecksumInstance() {
        return isStackTrace ? new ChecksumException() : INSTANCE;
    }

    public static ChecksumException getChecksumInstance(Throwable cause) {
        return isStackTrace ? new ChecksumException(cause) : INSTANCE;
    }
}
