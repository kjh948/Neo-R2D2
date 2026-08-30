package com.koushikdutta.async.http.server;

import android.net.Uri;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.callback.HttpConnectCallback;

/* JADX INFO: loaded from: classes.dex */
public class AsyncProxyServer extends AsyncHttpServer {
    AsyncHttpClient proxyClient;

    public AsyncProxyServer(AsyncServer server) {
        this.proxyClient = new AsyncHttpClient(server);
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServer
    protected void onRequest(HttpServerRequestCallback callback, AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
        Uri uri;
        super.onRequest(callback, request, response);
        if (callback == null) {
            try {
                try {
                    uri = Uri.parse(request.getPath());
                } catch (Exception e) {
                    String host = request.getHeaders().get("Host");
                    int port = 80;
                    if (host != null) {
                        String[] splits = host.split(":", 2);
                        if (splits.length == 2) {
                            host = splits[0];
                            port = Integer.parseInt(splits[1]);
                        }
                    }
                    uri = Uri.parse("http://" + host + ":" + port + request.getPath());
                }
                if (uri.getScheme() == null) {
                    throw new Exception("no host or full uri provided");
                }
                this.proxyClient.execute(new AsyncHttpRequest(uri, request.getMethod(), request.getHeaders()), new HttpConnectCallback() { // from class: com.koushikdutta.async.http.server.AsyncProxyServer.1
                    @Override // com.koushikdutta.async.http.callback.HttpConnectCallback
                    public void onConnectCompleted(Exception ex, AsyncHttpResponse remoteResponse) {
                        if (ex != null) {
                            response.code(500);
                            response.send(ex.getMessage());
                        } else {
                            response.proxy(remoteResponse);
                        }
                    }
                });
                return;
            } catch (Exception e2) {
                response.code(500);
                response.send(e2.getMessage());
            }
            response.code(500);
            response.send(e2.getMessage());
        }
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServer
    protected boolean onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        return true;
    }
}
