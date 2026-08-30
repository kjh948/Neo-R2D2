package com.koushikdutta.async.http;

/* JADX INFO: loaded from: classes.dex */
public interface RequestLine {
    String getMethod();

    ProtocolVersion getProtocolVersion();

    String getUri();
}
