package com.koushikdutta.async.http;

import android.net.Uri;

/* JADX INFO: loaded from: classes.dex */
public class AsyncHttpGet extends AsyncHttpRequest {
    public static final String METHOD = "GET";

    public AsyncHttpGet(String uri) {
        super(Uri.parse(uri), METHOD);
    }

    public AsyncHttpGet(Uri uri) {
        super(uri, METHOD);
    }
}
