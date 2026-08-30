package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.NameValuePair;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public abstract class StreamPart extends Part {
    protected abstract InputStream getInputStream() throws IOException;

    public StreamPart(String name, long length, List<NameValuePair> contentDisposition) {
        super(name, length, contentDisposition);
    }

    @Override // com.koushikdutta.async.http.body.Part
    public void write(DataSink sink, CompletedCallback callback) {
        try {
            InputStream is = getInputStream();
            Util.pump(is, sink, callback);
        } catch (Exception e) {
            callback.onCompleted(e);
        }
    }
}
