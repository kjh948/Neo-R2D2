package com.koushikdutta.async.parser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.TransformFuture;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

/* JADX INFO: loaded from: classes.dex */
public class StringParser implements AsyncParser<String> {
    Charset forcedCharset;

    public StringParser() {
    }

    public StringParser(Charset charset) {
        this.forcedCharset = charset;
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public Future<String> parse(DataEmitter emitter) {
        final String charset = emitter.charset();
        return (Future) new ByteBufferListParser().parse(emitter).then(new TransformFuture<String, ByteBufferList>() { // from class: com.koushikdutta.async.parser.StringParser.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // com.koushikdutta.async.future.TransformFuture
            public void transform(ByteBufferList result) throws Exception {
                Charset charsetToUse = StringParser.this.forcedCharset;
                if (charsetToUse == null && charset != null) {
                    charsetToUse = Charset.forName(charset);
                }
                setComplete(result.readString(charsetToUse));
            }
        });
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public void write(DataSink sink, String value, CompletedCallback completed) {
        new ByteBufferListParser().write(sink, new ByteBufferList(value.getBytes()), completed);
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public Type getType() {
        return String.class;
    }
}
