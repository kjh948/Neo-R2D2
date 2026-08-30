package com.koushikdutta.async.http.spdy;

import com.koushikdutta.async.http.Protocol;
import java.util.List;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
final class SpdyTransport {
    private static final List<String> SPDY_3_PROHIBITED_HEADERS = Util.immutableList("connection", "host", "keep-alive", "proxy-connection", "transfer-encoding");
    private static final List<String> HTTP_2_PROHIBITED_HEADERS = Util.immutableList("connection", "host", "keep-alive", "proxy-connection", "te", "transfer-encoding", "encoding", "upgrade");

    SpdyTransport() {
    }

    static boolean isProhibitedHeader(Protocol protocol, String name) {
        if (protocol == Protocol.SPDY_3) {
            return SPDY_3_PROHIBITED_HEADERS.contains(name.toLowerCase(Locale.US));
        }
        if (protocol == Protocol.HTTP_2) {
            return HTTP_2_PROHIBITED_HEADERS.contains(name.toLowerCase(Locale.US));
        }
        throw new AssertionError(protocol);
    }
}
