package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import java.io.File;

/* JADX INFO: loaded from: classes.dex */
public class FileBody implements AsyncHttpRequestBody<File> {
    public static final String CONTENT_TYPE = "application/binary";
    String contentType;
    File file;

    public FileBody(File file) {
        this.contentType = "application/binary";
        this.file = file;
    }

    public FileBody(File file, String contentType) {
        this.contentType = "application/binary";
        this.file = file;
        this.contentType = contentType;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        Util.pump(this.file, sink, completed);
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        throw new AssertionError("not implemented");
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        throw new AssertionError("not implemented");
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        return (int) this.file.length();
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public File get() {
        return this.file;
    }
}
