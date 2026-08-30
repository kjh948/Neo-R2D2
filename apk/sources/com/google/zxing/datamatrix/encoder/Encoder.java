package com.google.zxing.datamatrix.encoder;

/* JADX INFO: loaded from: classes.dex */
interface Encoder {
    void encode(EncoderContext encoderContext);

    int getEncodingMode();
}
