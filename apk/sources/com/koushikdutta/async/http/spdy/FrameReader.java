package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.ByteBufferList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
interface FrameReader {

    public interface Handler {
        void ackSettings();

        void alternateService(int i, String str, ByteString byteString, String str2, int i2, long j);

        void data(boolean z, int i, ByteBufferList byteBufferList);

        void error(Exception exc);

        void goAway(int i, ErrorCode errorCode, ByteString byteString);

        void headers(boolean z, boolean z2, int i, int i2, List<Header> list, HeadersMode headersMode);

        void ping(boolean z, int i, int i2);

        void priority(int i, int i2, int i3, boolean z);

        void pushPromise(int i, int i2, List<Header> list);

        void rstStream(int i, ErrorCode errorCode);

        void settings(boolean z, Settings settings);

        void windowUpdate(int i, long j);
    }
}
