package com.koushikdutta.async.http;

import android.text.TextUtils;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.LineEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import java.io.IOException;

/* JADX INFO: loaded from: classes.dex */
public class HttpTransportMiddleware extends SimpleMiddleware {
    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public boolean exchangeHeaders(final AsyncHttpClientMiddleware.OnExchangeHeaderData data) {
        final BufferedDataSink bsink;
        DataSink headerSink;
        Protocol p = Protocol.get(data.protocol);
        if (p != null && p != Protocol.HTTP_1_0 && p != Protocol.HTTP_1_1) {
            return super.exchangeHeaders(data);
        }
        AsyncHttpRequest request = data.request;
        AsyncHttpRequestBody requestBody = data.request.getBody();
        if (requestBody != null) {
            if (requestBody.length() >= 0) {
                request.getHeaders().set("Content-Length", String.valueOf(requestBody.length()));
                data.response.sink(data.socket);
            } else if ("close".equals(request.getHeaders().get("Connection"))) {
                data.response.sink(data.socket);
            } else {
                request.getHeaders().set("Transfer-Encoding", "Chunked");
                data.response.sink(new ChunkedOutputFilter(data.socket));
            }
        }
        String rl = request.getRequestLine().toString();
        String rs = request.getHeaders().toPrefixString(rl);
        byte[] rsBytes = rs.getBytes();
        boolean waitForBody = requestBody != null && requestBody.length() >= 0 && requestBody.length() + rsBytes.length < 1024;
        if (waitForBody) {
            bsink = new BufferedDataSink(data.response.sink());
            bsink.forceBuffering(true);
            data.response.sink(bsink);
            headerSink = bsink;
        } else {
            bsink = null;
            headerSink = data.socket;
        }
        request.logv("\n" + rs);
        final CompletedCallback sentCallback = data.sendHeadersCallback;
        Util.writeAll(headerSink, rsBytes, new CompletedCallback() { // from class: com.koushikdutta.async.http.HttpTransportMiddleware.1
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                Util.end(sentCallback, ex);
                if (bsink != null) {
                    bsink.forceBuffering(false);
                    bsink.setMaxBuffer(0);
                }
            }
        });
        LineEmitter.StringCallback headerCallback = new LineEmitter.StringCallback() { // from class: com.koushikdutta.async.http.HttpTransportMiddleware.2
            Headers mRawHeaders = new Headers();
            String statusLine;

            @Override // com.koushikdutta.async.LineEmitter.StringCallback
            public void onStringAvailable(String s) {
                DataEmitter emitter;
                try {
                    String s2 = s.trim();
                    if (this.statusLine == null) {
                        this.statusLine = s2;
                        return;
                    }
                    if (!TextUtils.isEmpty(s2)) {
                        this.mRawHeaders.addLine(s2);
                        return;
                    }
                    String[] parts = this.statusLine.split(" ", 3);
                    if (parts.length < 2) {
                        throw new Exception(new IOException("Not HTTP"));
                    }
                    data.response.headers(this.mRawHeaders);
                    String protocol = parts[0];
                    data.response.protocol(protocol);
                    data.response.code(Integer.parseInt(parts[1]));
                    data.response.message(parts.length == 3 ? parts[2] : "");
                    data.receiveHeadersCallback.onCompleted(null);
                    AsyncSocket socket = data.response.socket();
                    if (socket != null) {
                        if (AsyncHttpHead.METHOD.equalsIgnoreCase(data.request.getMethod())) {
                            emitter = HttpUtil.EndEmitter.create(socket.getServer(), null);
                        } else {
                            emitter = HttpUtil.getBodyDecoder(socket, Protocol.get(protocol), this.mRawHeaders, false);
                        }
                        data.response.emitter(emitter);
                    }
                } catch (Exception ex) {
                    data.receiveHeadersCallback.onCompleted(ex);
                }
            }
        };
        LineEmitter liner = new LineEmitter();
        data.socket.setDataCallback(liner);
        liner.setLineCallback(headerCallback);
        return true;
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onRequestSent(AsyncHttpClientMiddleware.OnRequestSentData data) {
        Protocol p = Protocol.get(data.protocol);
        if ((p == null || p == Protocol.HTTP_1_0 || p == Protocol.HTTP_1_1) && (data.response.sink() instanceof ChunkedOutputFilter)) {
            data.response.sink().end();
        }
    }
}
