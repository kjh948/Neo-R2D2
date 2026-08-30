package com.koushikdutta.async.http;

import java.util.Hashtable;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public enum Protocol {
    HTTP_1_0("http/1.0"),
    HTTP_1_1("http/1.1"),
    SPDY_3("spdy/3.1") { // from class: com.koushikdutta.async.http.Protocol.1
        @Override // com.koushikdutta.async.http.Protocol
        public boolean needsSpdyConnection() {
            return true;
        }
    },
    HTTP_2("h2-13") { // from class: com.koushikdutta.async.http.Protocol.2
        @Override // com.koushikdutta.async.http.Protocol
        public boolean needsSpdyConnection() {
            return true;
        }
    };

    private static final Hashtable<String, Protocol> protocols = new Hashtable<>();
    private final String protocol;

    static {
        protocols.put(HTTP_1_0.toString(), HTTP_1_0);
        protocols.put(HTTP_1_1.toString(), HTTP_1_1);
        protocols.put(SPDY_3.toString(), SPDY_3);
        protocols.put(HTTP_2.toString(), HTTP_2);
    }

    Protocol(String protocol) {
        this.protocol = protocol;
    }

    public static Protocol get(String protocol) {
        if (protocol == null) {
            return null;
        }
        return protocols.get(protocol.toLowerCase(Locale.US));
    }

    @Override // java.lang.Enum
    public String toString() {
        return this.protocol;
    }

    public boolean needsSpdyConnection() {
        return false;
    }
}
