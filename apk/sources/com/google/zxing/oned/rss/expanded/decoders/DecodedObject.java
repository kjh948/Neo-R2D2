package com.google.zxing.oned.rss.expanded.decoders;

/* JADX INFO: loaded from: classes.dex */
abstract class DecodedObject {
    private final int newPosition;

    DecodedObject(int newPosition) {
        this.newPosition = newPosition;
    }

    final int getNewPosition() {
        return this.newPosition;
    }
}
