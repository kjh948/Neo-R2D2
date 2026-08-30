package com.koushikdutta.async.http.body;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.NameValuePair;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class UrlEncodedFormBody implements AsyncHttpRequestBody<Multimap> {
    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private byte[] mBodyBytes;
    private Multimap mParameters;

    public UrlEncodedFormBody(Multimap parameters) {
        this.mParameters = parameters;
    }

    public UrlEncodedFormBody(List<NameValuePair> parameters) {
        this.mParameters = new Multimap(parameters);
    }

    private void buildData() {
        boolean first = true;
        StringBuilder b = new StringBuilder();
        try {
            for (NameValuePair pair : this.mParameters) {
                if (pair.getValue() != null) {
                    if (!first) {
                        b.append('&');
                    }
                    first = false;
                    b.append(URLEncoder.encode(pair.getName(), "UTF-8"));
                    b.append('=');
                    b.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
                }
            }
            this.mBodyBytes = b.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, DataSink response, CompletedCallback completed) {
        if (this.mBodyBytes == null) {
            buildData();
        }
        Util.writeAll(response, this.mBodyBytes, completed);
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        return "application/x-www-form-urlencoded; charset=utf-8";
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        final ByteBufferList data = new ByteBufferList();
        emitter.setDataCallback(new DataCallback() { // from class: com.koushikdutta.async.http.body.UrlEncodedFormBody.1
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter2, ByteBufferList bb) {
                bb.get(data);
            }
        });
        emitter.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.http.body.UrlEncodedFormBody.2
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    completed.onCompleted(ex);
                    return;
                }
                try {
                    UrlEncodedFormBody.this.mParameters = Multimap.parseUrlEncoded(data.readString());
                    completed.onCompleted(null);
                } catch (Exception e) {
                    completed.onCompleted(e);
                }
            }
        });
    }

    public UrlEncodedFormBody() {
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        if (this.mBodyBytes == null) {
            buildData();
        }
        return this.mBodyBytes.length;
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public Multimap get() {
        return this.mParameters;
    }
}
