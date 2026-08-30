package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.util.UntypedHashtable;

/* JADX INFO: loaded from: classes.dex */
public interface AsyncHttpClientMiddleware {

    public static class GetSocketData extends OnRequestData {
        public ConnectCallback connectCallback;
        public String protocol;
        public Cancellable socketCancellable;
    }

    public static class OnBodyDataOnRequestSentData extends OnHeadersReceivedDataOnRequestSentData {
        public DataEmitter bodyEmitter;
    }

    public static class OnExchangeHeaderData extends GetSocketData {
        public CompletedCallback receiveHeadersCallback;
        public ResponseHead response;
        public CompletedCallback sendHeadersCallback;
        public AsyncSocket socket;
    }

    public static class OnHeadersReceivedDataOnRequestSentData extends OnRequestSentData {
    }

    public static class OnRequestData {
        public AsyncHttpRequest request;
        public UntypedHashtable state = new UntypedHashtable();
    }

    public static class OnRequestSentData extends OnExchangeHeaderData {
    }

    public static class OnResponseCompleteDataOnRequestSentData extends OnBodyDataOnRequestSentData {
        public Exception exception;
    }

    public interface ResponseHead {
        int code();

        ResponseHead code(int i);

        DataEmitter emitter();

        ResponseHead emitter(DataEmitter dataEmitter);

        ResponseHead headers(Headers headers);

        Headers headers();

        ResponseHead message(String str);

        String message();

        ResponseHead protocol(String str);

        String protocol();

        DataSink sink();

        ResponseHead sink(DataSink dataSink);

        AsyncSocket socket();
    }

    boolean exchangeHeaders(OnExchangeHeaderData onExchangeHeaderData);

    Cancellable getSocket(GetSocketData getSocketData);

    void onBodyDecoder(OnBodyDataOnRequestSentData onBodyDataOnRequestSentData);

    void onHeadersReceived(OnHeadersReceivedDataOnRequestSentData onHeadersReceivedDataOnRequestSentData);

    void onRequest(OnRequestData onRequestData);

    void onRequestSent(OnRequestSentData onRequestSentData);

    void onResponseComplete(OnResponseCompleteDataOnRequestSentData onResponseCompleteDataOnRequestSentData);
}
