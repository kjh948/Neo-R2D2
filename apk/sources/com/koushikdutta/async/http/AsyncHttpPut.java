package com.koushikdutta.async.http;

import android.net.Uri;

/* JADX INFO: loaded from: classes.dex */
public class AsyncHttpPut extends AsyncHttpRequest {
    public static final String METHOD = "PUT";

    public AsyncHttpPut(String uri) {
        this(Uri.parse(uri));
    }

    public AsyncHttpPut(Uri uri) {
        super(uri, METHOD);
    }
}
