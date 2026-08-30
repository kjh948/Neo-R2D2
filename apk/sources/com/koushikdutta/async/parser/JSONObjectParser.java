package com.koushikdutta.async.parser;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.TransformFuture;
import java.lang.reflect.Type;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class JSONObjectParser implements AsyncParser<JSONObject> {
    @Override // com.koushikdutta.async.parser.AsyncParser
    public Future<JSONObject> parse(DataEmitter emitter) {
        return (Future) new StringParser().parse(emitter).then(new TransformFuture<JSONObject, String>() { // from class: com.koushikdutta.async.parser.JSONObjectParser.1
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // com.koushikdutta.async.future.TransformFuture
            public void transform(String result) throws Exception {
                setComplete(new JSONObject(result));
            }
        });
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public void write(DataSink sink, JSONObject value, CompletedCallback completed) {
        new StringParser().write(sink, value.toString(), completed);
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public Type getType() {
        return JSONObject.class;
    }
}
