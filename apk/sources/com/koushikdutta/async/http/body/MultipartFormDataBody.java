package com.koushikdutta.async.http.body;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ContinuationCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Continuation;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.server.BoundaryEmitter;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

/* JADX INFO: loaded from: classes.dex */
public class MultipartFormDataBody extends BoundaryEmitter implements AsyncHttpRequestBody<Multimap> {
    public static final String CONTENT_TYPE = "multipart/form-data";
    String contentType = CONTENT_TYPE;
    Headers formData;
    ByteBufferList last;
    String lastName;
    LineEmitter liner;
    MultipartCallback mCallback;
    private ArrayList<Part> mParts;
    int totalToWrite;
    int written;

    public interface MultipartCallback {
        void onPart(Part part);
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, CompletedCallback completed) {
        setDataEmitter(emitter);
        setEndCallback(completed);
    }

    void handleLast() {
        if (this.last != null) {
            if (this.formData == null) {
                this.formData = new Headers();
            }
            this.formData.add(this.lastName, this.last.peekString());
            this.lastName = null;
            this.last = null;
        }
    }

    public String getField(String name) {
        if (this.formData == null) {
            return null;
        }
        return this.formData.get(name);
    }

    @Override // com.koushikdutta.async.http.server.BoundaryEmitter
    protected void onBoundaryEnd() {
        super.onBoundaryEnd();
        handleLast();
    }

    @Override // com.koushikdutta.async.http.server.BoundaryEmitter
    protected void onBoundaryStart() {
        final Headers headers = new Headers();
        this.liner = new LineEmitter();
        this.liner.setLineCallback(new LineEmitter.StringCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.1
            @Override // com.koushikdutta.async.LineEmitter.StringCallback
            public void onStringAvailable(String s) {
                if (!"\r".equals(s)) {
                    headers.addLine(s);
                    return;
                }
                MultipartFormDataBody.this.handleLast();
                MultipartFormDataBody.this.liner = null;
                MultipartFormDataBody.this.setDataCallback(null);
                Part part = new Part(headers);
                if (MultipartFormDataBody.this.mCallback != null) {
                    MultipartFormDataBody.this.mCallback.onPart(part);
                }
                if (MultipartFormDataBody.this.getDataCallback() == null) {
                    if (part.isFile()) {
                        MultipartFormDataBody.this.setDataCallback(new DataCallback.NullDataCallback());
                        return;
                    }
                    MultipartFormDataBody.this.lastName = part.getName();
                    MultipartFormDataBody.this.last = new ByteBufferList();
                    MultipartFormDataBody.this.setDataCallback(new DataCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.1.1
                        @Override // com.koushikdutta.async.callback.DataCallback
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            bb.get(MultipartFormDataBody.this.last);
                        }
                    });
                }
            }
        });
        setDataCallback(this.liner);
    }

    public MultipartFormDataBody(String[] values) {
        for (String value : values) {
            String[] splits = value.split("=");
            if (splits.length == 2 && "boundary".equals(splits[0])) {
                setBoundary(splits[1]);
                return;
            }
        }
        report(new Exception("No boundary found for multipart/form-data"));
    }

    public void setMultipartCallback(MultipartCallback callback) {
        this.mCallback = callback;
    }

    public MultipartCallback getMultipartCallback() {
        return this.mCallback;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, final DataSink sink, final CompletedCallback completed) {
        if (this.mParts != null) {
            Continuation c = new Continuation(new CompletedCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.2
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    completed.onCompleted(ex);
                }
            });
            for (final Part part : this.mParts) {
                c.add(new ContinuationCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.5
                    @Override // com.koushikdutta.async.callback.ContinuationCallback
                    public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                        byte[] bytes = part.getRawHeaders().toPrefixString(MultipartFormDataBody.this.getBoundaryStart()).getBytes();
                        Util.writeAll(sink, bytes, next);
                        MultipartFormDataBody.this.written += bytes.length;
                    }
                }).add(new ContinuationCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.4
                    @Override // com.koushikdutta.async.callback.ContinuationCallback
                    public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                        long partLength = part.length();
                        if (partLength >= 0) {
                            MultipartFormDataBody multipartFormDataBody = MultipartFormDataBody.this;
                            multipartFormDataBody.written = (int) (((long) multipartFormDataBody.written) + partLength);
                        }
                        part.write(sink, next);
                    }
                }).add(new ContinuationCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.3
                    @Override // com.koushikdutta.async.callback.ContinuationCallback
                    public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                        byte[] bytes = "\r\n".getBytes();
                        Util.writeAll(sink, bytes, next);
                        MultipartFormDataBody.this.written += bytes.length;
                    }
                });
            }
            c.add(new ContinuationCallback() { // from class: com.koushikdutta.async.http.body.MultipartFormDataBody.6
                static final /* synthetic */ boolean $assertionsDisabled;

                static {
                    $assertionsDisabled = !MultipartFormDataBody.class.desiredAssertionStatus();
                }

                @Override // com.koushikdutta.async.callback.ContinuationCallback
                public void onContinue(Continuation continuation, CompletedCallback next) throws Exception {
                    byte[] bytes = MultipartFormDataBody.this.getBoundaryEnd().getBytes();
                    Util.writeAll(sink, bytes, next);
                    MultipartFormDataBody.this.written += bytes.length;
                    if (!$assertionsDisabled && MultipartFormDataBody.this.written != MultipartFormDataBody.this.totalToWrite) {
                        throw new AssertionError();
                    }
                }
            });
            c.start();
        }
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        if (getBoundary() == null) {
            setBoundary("----------------------------" + UUID.randomUUID().toString().replace("-", ""));
        }
        return this.contentType + "; boundary=" + getBoundary();
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        return false;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        if (getBoundary() == null) {
            setBoundary("----------------------------" + UUID.randomUUID().toString().replace("-", ""));
        }
        int length = 0;
        for (Part part : this.mParts) {
            String partHeader = part.getRawHeaders().toPrefixString(getBoundaryStart());
            if (part.length() == -1) {
                return -1;
            }
            length = (int) (((long) length) + part.length() + ((long) partHeader.getBytes().length) + ((long) "\r\n".length()));
        }
        int length2 = length + getBoundaryEnd().getBytes().length;
        this.totalToWrite = length2;
        return length2;
    }

    public MultipartFormDataBody() {
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void addFilePart(String name, File file) {
        addPart(new FilePart(name, file));
    }

    public void addStringPart(String name, String value) {
        addPart(new StringPart(name, value));
    }

    public void addPart(Part part) {
        if (this.mParts == null) {
            this.mParts = new ArrayList<>();
        }
        this.mParts.add(part);
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public Multimap get() {
        return new Multimap(this.formData.getMultiMap());
    }
}
