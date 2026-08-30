package com.koushikdutta.async.parser;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.SimpleFuture;
import java.lang.reflect.Type;

/* JADX INFO: loaded from: classes.dex */
public class ByteBufferListParser implements AsyncParser<ByteBufferList> {
    @Override // com.koushikdutta.async.parser.AsyncParser
    public Future<ByteBufferList> parse(final DataEmitter emitter) {
        final ByteBufferList bb = new ByteBufferList();
        final SimpleFuture<ByteBufferList> ret = new SimpleFuture<ByteBufferList>() { // from class: com.koushikdutta.async.parser.ByteBufferListParser.1
            @Override // com.koushikdutta.async.future.SimpleCancellable
            protected void cancelCleanup() {
                emitter.close();
            }
        };
        emitter.setDataCallback(new DataCallback() { // from class: com.koushikdutta.async.parser.ByteBufferListParser.2
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter2, ByteBufferList data) {
                data.get(bb);
            }
        });
        emitter.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.parser.ByteBufferListParser.3
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    ret.setComplete(ex);
                    return;
                }
                try {
                    ret.setComplete(bb);
                } catch (Exception e) {
                    ret.setComplete(e);
                }
            }
        });
        return ret;
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public void write(DataSink sink, ByteBufferList value, CompletedCallback completed) {
        Util.writeAll(sink, value, completed);
    }

    @Override // com.koushikdutta.async.parser.AsyncParser
    public Type getType() {
        return ByteBufferList.class;
    }
}
