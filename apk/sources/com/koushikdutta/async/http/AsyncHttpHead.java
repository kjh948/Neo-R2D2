package com.koushikdutta.async.http;

import android.net.Uri;

/* JADX INFO: loaded from: classes.dex */
public class AsyncHttpHead extends AsyncHttpRequest {
    public static final String METHOD = "HEAD";

    public AsyncHttpHead(Uri uri) {
        super(uri, METHOD);
    }
}
