package org.java_websocket.exceptions;

/* JADX INFO: loaded from: classes.dex */
public class IncompleteHandshakeException extends RuntimeException {
    private static final long serialVersionUID = 7906596804233893092L;
    private int preferedSize;

    public IncompleteHandshakeException(int preferedSize) {
        this.preferedSize = preferedSize;
    }

    public IncompleteHandshakeException() {
        this.preferedSize = 0;
    }

    public int getPreferedSize() {
        return this.preferedSize;
    }
}
