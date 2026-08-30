package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
interface FrameWriter extends Closeable {
    void ackSettings() throws IOException;

    void connectionPreface() throws IOException;

    void data(boolean z, int i, ByteBufferList byteBufferList) throws IOException;

    void goAway(int i, ErrorCode errorCode, byte[] bArr) throws IOException;

    void headers(int i, List<Header> list) throws IOException;

    void ping(boolean z, int i, int i2) throws IOException;

    void pushPromise(int i, int i2, List<Header> list) throws IOException;

    void rstStream(int i, ErrorCode errorCode) throws IOException;

    void settings(Settings settings) throws IOException;

    void synReply(boolean z, int i, List<Header> list) throws IOException;

    void synStream(boolean z, boolean z2, int i, int i2, List<Header> list) throws IOException;

    void windowUpdate(int i, long j) throws IOException;
}
