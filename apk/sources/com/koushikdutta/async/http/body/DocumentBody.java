package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.parser.DocumentParser;
import com.koushikdutta.async.util.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

/* JADX INFO: loaded from: classes.dex */
public class DocumentBody implements AsyncHttpRequestBody<Document> {
    public static final String CONTENT_TYPE = "application/xml";
    ByteArrayOutputStream bout;
    Document document;

    public DocumentBody() {
        this(null);
    }

    public DocumentBody(Document document) {
        this.document = document;
    }

    private void prepare() {
        if (this.bout == null) {
            try {
                DOMSource source = new DOMSource(this.document);
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                this.bout = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(this.bout, Charsets.UTF_8);
                StreamResult result = new StreamResult(writer);
                transformer.transform(source, result);
                writer.flush();
            } catch (Exception e) {
            }
        }
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void write(AsyncHttpRequest request, DataSink sink, CompletedCallback completed) {
        prepare();
        byte[] bytes = this.bout.toByteArray();
        Util.writeAll(sink, bytes, completed);
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public void parse(DataEmitter emitter, final CompletedCallback completed) {
        new DocumentParser().parse(emitter).setCallback(new FutureCallback<Document>() { // from class: com.koushikdutta.async.http.body.DocumentBody.1
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, Document result) {
                DocumentBody.this.document = result;
                completed.onCompleted(e);
            }
        });
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public boolean readFullyOnRequest() {
        return true;
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public int length() {
        prepare();
        return this.bout.size();
    }

    @Override // com.koushikdutta.async.http.body.AsyncHttpRequestBody
    public Document get() {
        return this.document;
    }
}
