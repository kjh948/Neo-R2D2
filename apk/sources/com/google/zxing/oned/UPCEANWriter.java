package com.google.zxing.oned;

/* JADX INFO: loaded from: classes.dex */
public abstract class UPCEANWriter extends OneDimensionalCodeWriter {
    @Override // com.google.zxing.oned.OneDimensionalCodeWriter
    public int getDefaultMargin() {
        return UPCEANReader.START_END_PATTERN.length;
    }
}
