package com.koushikdutta.async.parser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.http.body.DocumentBody;
import com.koushikdutta.async.stream.ByteBufferListInputStream;
import java.lang.reflect.Type;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/* JADX INFO: loaded from: classes.dex */
public class DocumentParser implements AsyncParser<Document> {
    @Override // com.koushikdutta.async.parser.AsyncParser
    public Future<Document> parse(DataEmitter emitter) {
        return (Future) new ByteBufferListParser().parse(emitter).then(new TransformFuture<Document, ByteBufferList>() { // from class: com.koushikdutta.async.parser.DocumentParser.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // com.koushikdutta.async.future.TransformFuture
            public void transform(ByteBufferList result) throws Exception {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                setComplete(db.parse(new ByteBufferListInputStream(result)));
            }
        });
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public void write(DataSink sink, Document value, CompletedCallback completed) {
        new DocumentBody(value).write(null, sink, completed);
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public Type getType() {
        return Document.class;
    }
}
