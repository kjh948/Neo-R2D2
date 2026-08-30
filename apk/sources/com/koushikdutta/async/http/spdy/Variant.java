package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.spdy.FrameReader;

/* JADX INFO: loaded from: classes.dex */
interface Variant {
    Protocol getProtocol();

    int maxFrameSize();

    FrameReader newReader(DataEmitter dataEmitter, FrameReader.Handler handler, boolean z);

    FrameWriter newWriter(BufferedDataSink bufferedDataSink, boolean z);
}
