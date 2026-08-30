package com.koushikdutta.async.http.cache;

/* JADX INFO: loaded from: classes.dex */
enum ResponseSource {
    CACHE,
    CONDITIONAL_CACHE,
    NETWORK;

    public boolean requiresConnection() {
        return this == CONDITIONAL_CACHE || this == NETWORK;
    }
}
