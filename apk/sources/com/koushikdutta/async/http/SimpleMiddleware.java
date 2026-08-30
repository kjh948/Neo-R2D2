package com.koushikdutta.async.http;

import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;

/* JADX INFO: loaded from: classes.dex */
public class SimpleMiddleware implements AsyncHttpClientMiddleware {
    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onRequest(AsyncHttpClientMiddleware.OnRequestData data) {
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public Cancellable getSocket(AsyncHttpClientMiddleware.GetSocketData data) {
        return null;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public boolean exchangeHeaders(AsyncHttpClientMiddleware.OnExchangeHeaderData data) {
        return false;
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onRequestSent(AsyncHttpClientMiddleware.OnRequestSentData data) {
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onHeadersReceived(AsyncHttpClientMiddleware.OnHeadersReceivedDataOnRequestSentData data) {
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onBodyDecoder(AsyncHttpClientMiddleware.OnBodyDataOnRequestSentData data) {
    }

    @Override // com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onResponseComplete(AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data) {
    }
}
