package com.koushikdutta.async.parser;

import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.future.Future;
import java.lang.reflect.Type;

/* JADX INFO: loaded from: classes.dex */
public interface AsyncParser<T> {
    Type getType();

    Future<T> parse(DataEmitter dataEmitter);

    void write(DataSink dataSink, T t, CompletedCallback completedCallback);
}
