package com.koushikdutta.async.http.server;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncSSLSocketWrapper;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpHead;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.WebSocketImpl;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.util.StreamUtility;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

/* JADX INFO: loaded from: classes.dex */
@TargetApi(5)
public class AsyncHttpServer {
    static final /* synthetic */ boolean $assertionsDisabled;
    private static Hashtable<Integer, String> mCodes;
    static Hashtable<String, String> mContentTypes;
    CompletedCallback mCompletedCallback;
    ArrayList<AsyncServerSocket> mListeners = new ArrayList<>();
    ListenCallback mListenCallback = new AnonymousClass1();
    final Hashtable<String, ArrayList<Pair>> mActions = new Hashtable<>();

    public interface WebSocketRequestCallback {
        void onConnected(WebSocket webSocket, AsyncHttpServerRequest asyncHttpServerRequest);
    }

    static {
        $assertionsDisabled = !AsyncHttpServer.class.desiredAssertionStatus();
        mContentTypes = new Hashtable<>();
        mCodes = new Hashtable<>();
        mCodes.put(200, "OK");
        mCodes.put(202, "Accepted");
        mCodes.put(206, "Partial Content");
        mCodes.put(101, "Switching Protocols");
        mCodes.put(301, "Moved Permanently");
        mCodes.put(302, "Found");
        mCodes.put(404, "Not Found");
    }

    public AsyncHttpServer() {
        mContentTypes.put("js", "application/javascript");
        mContentTypes.put("json", "application/json");
        mContentTypes.put("png", "image/png");
        mContentTypes.put("jpg", "image/jpeg");
        mContentTypes.put("html", "text/html");
        mContentTypes.put("css", "text/css");
        mContentTypes.put("mp4", "video/mp4");
        mContentTypes.put("mov", "video/quicktime");
        mContentTypes.put("wmv", "video/x-ms-wmv");
    }

    public void stop() {
        if (this.mListeners != null) {
            for (AsyncServerSocket listener : this.mListeners) {
                listener.stop();
            }
        }
    }

    protected boolean onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        return false;
    }

    protected void onRequest(HttpServerRequestCallback callback, AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        if (callback != null) {
            callback.onRequest(request, response);
        }
    }

    protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
        return new UnknownRequestBody(headers.get("Content-Type"));
    }

    /* JADX INFO: renamed from: com.koushikdutta.async.http.server.AsyncHttpServer$1, reason: invalid class name */
    class AnonymousClass1 implements ListenCallback {
        AnonymousClass1() {
        }

        /* JADX INFO: renamed from: com.koushikdutta.async.http.server.AsyncHttpServer$1$1, reason: invalid class name and collision with other inner class name */
        class C00091 extends AsyncHttpServerRequestImpl {
            String fullPath;
            boolean hasContinued;
            HttpServerRequestCallback match;
            String path;
            boolean requestComplete;
            AsyncHttpServerResponseImpl res;
            boolean responseComplete;
            final /* synthetic */ AsyncSocket val$socket;

            C00091(AsyncSocket asyncSocket) {
                this.val$socket = asyncSocket;
            }

            @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequestImpl
            protected AsyncHttpRequestBody onUnknownBody(Headers headers) {
                return AsyncHttpServer.this.onUnknownBody(headers);
            }

            @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequestImpl
            protected void onHeadersReceived() {
                Headers headers = getHeaders();
                if (!this.hasContinued && "100-continue".equals(headers.get("Expect"))) {
                    pause();
                    Util.writeAll(this.mSocket, "HTTP/1.1 100 Continue\r\n\r\n".getBytes(), new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.1.1.1
                        @Override // com.koushikdutta.async.callback.CompletedCallback
                        public void onCompleted(Exception ex) {
                            C00091.this.resume();
                            if (ex != null) {
                                C00091.this.report(ex);
                            } else {
                                C00091.this.hasContinued = true;
                                C00091.this.onHeadersReceived();
                            }
                        }
                    });
                    return;
                }
                String statusLine = getStatusLine();
                String[] parts = statusLine.split(" ");
                this.fullPath = parts[1];
                this.path = this.fullPath.split("\\?")[0];
                this.method = parts[0];
                synchronized (AsyncHttpServer.this.mActions) {
                    ArrayList<Pair> pairs = AsyncHttpServer.this.mActions.get(this.method);
                    if (pairs != null) {
                        Iterator<Pair> it = pairs.iterator();
                        while (true) {
                            if (!it.hasNext()) {
                                break;
                            }
                            Pair p = it.next();
                            Matcher m = p.regex.matcher(this.path);
                            if (m.matches()) {
                                this.mMatcher = m;
                                this.match = p.callback;
                                break;
                            }
                        }
                    }
                }
                this.res = new AsyncHttpServerResponseImpl(this.val$socket, this) { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.1.1.2
                    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl
                    protected void report(Exception e) {
                        super.report(e);
                        if (e != null) {
                            C00091.this.val$socket.setDataCallback(new DataCallback.NullDataCallback());
                            C00091.this.val$socket.setEndCallback(new CompletedCallback.NullCompletedCallback());
                            C00091.this.val$socket.close();
                        }
                    }

                    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl
                    protected void onEnd() {
                        super.onEnd();
                        this.mSocket.setEndCallback(null);
                        C00091.this.responseComplete = true;
                        C00091.this.handleOnCompleted();
                    }
                };
                boolean handled = AsyncHttpServer.this.onRequest(this, this.res);
                if (this.match == null && !handled) {
                    this.res.code(404);
                    this.res.end();
                } else if (!getBody().readFullyOnRequest()) {
                    AsyncHttpServer.this.onRequest(this.match, this, this.res);
                } else if (this.requestComplete) {
                    AsyncHttpServer.this.onRequest(this.match, this, this.res);
                }
            }

            @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequestImpl, com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception e) {
                if (this.res.code() != 101) {
                    this.requestComplete = true;
                    super.onCompleted(e);
                    this.mSocket.setDataCallback(new DataCallback.NullDataCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.1.1.3
                        @Override // com.koushikdutta.async.callback.DataCallback.NullDataCallback, com.koushikdutta.async.callback.DataCallback
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                            super.onDataAvailable(emitter, bb);
                            C00091.this.mSocket.close();
                        }
                    });
                    handleOnCompleted();
                    if (getBody().readFullyOnRequest()) {
                        AsyncHttpServer.this.onRequest(this.match, this, this.res);
                    }
                }
            }

            /* JADX INFO: Access modifiers changed from: private */
            public void handleOnCompleted() {
                if (this.requestComplete && this.responseComplete) {
                    if (HttpUtil.isKeepAlive(Protocol.HTTP_1_1, getHeaders())) {
                        AnonymousClass1.this.onAccepted(this.val$socket);
                    } else {
                        this.val$socket.close();
                    }
                }
            }

            @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
            public String getPath() {
                return this.path;
            }

            @Override // com.koushikdutta.async.http.server.AsyncHttpServerRequest
            public Multimap getQuery() {
                String[] parts = this.fullPath.split("\\?", 2);
                return parts.length < 2 ? new Multimap() : Multimap.parseQuery(parts[1]);
            }
        }

        @Override // com.koushikdutta.async.callback.ListenCallback
        public void onAccepted(AsyncSocket socket) {
            AsyncHttpServerRequestImpl req = new C00091(socket);
            req.setSocket(socket);
            socket.resume();
        }

        @Override // com.koushikdutta.async.callback.CompletedCallback
        public void onCompleted(Exception error) {
            AsyncHttpServer.this.report(error);
        }

        @Override // com.koushikdutta.async.callback.ListenCallback
        public void onListening(AsyncServerSocket socket) {
            AsyncHttpServer.this.mListeners.add(socket);
        }
    }

    public AsyncServerSocket listen(AsyncServer server, int port) {
        return server.listen(null, port, this.mListenCallback);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void report(Exception ex) {
        if (this.mCompletedCallback != null) {
            this.mCompletedCallback.onCompleted(ex);
        }
    }

    public AsyncServerSocket listen(int port) {
        return listen(AsyncServer.getDefault(), port);
    }

    public void listenSecure(final int port, final SSLContext sslContext) {
        AsyncServer.getDefault().listen(null, port, new ListenCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.2
            @Override // com.koushikdutta.async.callback.ListenCallback
            public void onAccepted(AsyncSocket socket) {
                AsyncSSLSocketWrapper.handshake(socket, null, port, sslContext.createSSLEngine(), null, null, false, new AsyncSSLSocketWrapper.HandshakeCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.2.1
                    @Override // com.koushikdutta.async.AsyncSSLSocketWrapper.HandshakeCallback
                    public void onHandshakeCompleted(Exception e, AsyncSSLSocket socket2) {
                        if (socket2 != null) {
                            AsyncHttpServer.this.mListenCallback.onAccepted(socket2);
                        }
                    }
                });
            }

            @Override // com.koushikdutta.async.callback.ListenCallback
            public void onListening(AsyncServerSocket socket) {
                AsyncHttpServer.this.mListenCallback.onListening(socket);
            }

            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                AsyncHttpServer.this.mListenCallback.onCompleted(ex);
            }
        });
    }

    public ListenCallback getListenCallback() {
        return this.mListenCallback;
    }

    public void setErrorCallback(CompletedCallback callback) {
        this.mCompletedCallback = callback;
    }

    public CompletedCallback getErrorCallback() {
        return this.mCompletedCallback;
    }

    private static class Pair {
        HttpServerRequestCallback callback;
        Pattern regex;

        private Pair() {
        }

        /* synthetic */ Pair(AnonymousClass1 x0) {
            this();
        }
    }

    public void removeAction(String action, String regex) {
        synchronized (this.mActions) {
            ArrayList<Pair> pairs = this.mActions.get(action);
            if (pairs != null) {
                for (int i = 0; i < pairs.size(); i++) {
                    Pair p = pairs.get(i);
                    if (regex.equals(p.regex.toString())) {
                        pairs.remove(i);
                        return;
                    }
                }
            }
        }
    }

    public void addAction(String action, String regex, HttpServerRequestCallback callback) {
        Pair p = new Pair(null);
        p.regex = Pattern.compile("^" + regex);
        p.callback = callback;
        synchronized (this.mActions) {
            ArrayList<Pair> pairs = this.mActions.get(action);
            if (pairs == null) {
                pairs = new ArrayList<>();
                this.mActions.put(action, pairs);
            }
            pairs.add(p);
        }
    }

    public void websocket(String regex, WebSocketRequestCallback callback) {
        websocket(regex, null, callback);
    }

    public void websocket(String regex, final String protocol, final WebSocketRequestCallback callback) {
        get(regex, new HttpServerRequestCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.3
            @Override // com.koushikdutta.async.http.server.HttpServerRequestCallback
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                boolean hasUpgrade = false;
                String connection = request.getHeaders().get("Connection");
                if (connection != null) {
                    String[] connections = connection.split(",");
                    int length = connections.length;
                    int i = 0;
                    while (true) {
                        if (i >= length) {
                            break;
                        }
                        String c = connections[i];
                        if (!"Upgrade".equalsIgnoreCase(c.trim())) {
                            i++;
                        } else {
                            hasUpgrade = true;
                            break;
                        }
                    }
                }
                if (!"websocket".equalsIgnoreCase(request.getHeaders().get("Upgrade")) || !hasUpgrade) {
                    response.code(404);
                    response.end();
                    return;
                }
                String peerProtocol = request.getHeaders().get("Sec-WebSocket-Protocol");
                if (!TextUtils.equals(protocol, peerProtocol)) {
                    response.code(404);
                    response.end();
                } else {
                    callback.onConnected(new WebSocketImpl(request, response), request);
                }
            }
        });
    }

    public void get(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpGet.METHOD, regex, callback);
    }

    public void post(String regex, HttpServerRequestCallback callback) {
        addAction(AsyncHttpPost.METHOD, regex, callback);
    }

    public static android.util.Pair<Integer, InputStream> getAssetStream(Context context, String asset) {
        AssetManager am = context.getAssets();
        try {
            InputStream is = am.open(asset);
            return new android.util.Pair<>(Integer.valueOf(is.available()), is);
        } catch (IOException e) {
            return null;
        }
    }

    public static String getContentType(String path) {
        String type = tryGetContentType(path);
        return type != null ? type : StringBody.CONTENT_TYPE;
    }

    public static String tryGetContentType(String path) {
        int index = path.lastIndexOf(".");
        if (index != -1) {
            String e = path.substring(index + 1);
            String ct = mContentTypes.get(e);
            if (ct != null) {
                return ct;
            }
        }
        return null;
    }

    public void directory(Context context, String regex, final String assetPath) {
        final Context _context = context.getApplicationContext();
        addAction(AsyncHttpGet.METHOD, regex, new HttpServerRequestCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.4
            @Override // com.koushikdutta.async.http.server.HttpServerRequestCallback
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                android.util.Pair<Integer, InputStream> pair = AsyncHttpServer.getAssetStream(_context, assetPath + path);
                if (pair == null || pair.second == null) {
                    response.code(404);
                    response.end();
                    return;
                }
                final InputStream is = (InputStream) pair.second;
                response.getHeaders().set("Content-Length", String.valueOf(pair.first));
                response.code(200);
                response.getHeaders().add("Content-Type", AsyncHttpServer.getContentType(assetPath + path));
                Util.pump(is, response, new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.4.1
                    @Override // com.koushikdutta.async.callback.CompletedCallback
                    public void onCompleted(Exception ex) {
                        response.end();
                        StreamUtility.closeQuietly(is);
                    }
                });
            }
        });
        addAction(AsyncHttpHead.METHOD, regex, new HttpServerRequestCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.5
            @Override // com.koushikdutta.async.http.server.HttpServerRequestCallback
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                android.util.Pair<Integer, InputStream> pair = AsyncHttpServer.getAssetStream(_context, assetPath + path);
                if (pair == null || pair.second == null) {
                    response.code(404);
                    response.end();
                    return;
                }
                InputStream is = (InputStream) pair.second;
                StreamUtility.closeQuietly(is);
                response.getHeaders().set("Content-Length", String.valueOf(pair.first));
                response.code(200);
                response.getHeaders().add("Content-Type", AsyncHttpServer.getContentType(assetPath + path));
                response.writeHead();
                response.end();
            }
        });
    }

    public void directory(String regex, File directory) {
        directory(regex, directory, false);
    }

    public void directory(String regex, final File directory, final boolean list) {
        if (!$assertionsDisabled && !directory.isDirectory()) {
            throw new AssertionError();
        }
        addAction(AsyncHttpGet.METHOD, regex, new HttpServerRequestCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.6
            @Override // com.koushikdutta.async.http.server.HttpServerRequestCallback
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                String path = request.getMatcher().replaceAll("");
                File file = new File(directory, path);
                if (file.isDirectory() && list) {
                    ArrayList<File> dirs = new ArrayList<>();
                    ArrayList<File> files = new ArrayList<>();
                    for (File f : file.listFiles()) {
                        if (f.isDirectory()) {
                            dirs.add(f);
                        } else {
                            files.add(f);
                        }
                    }
                    Comparator<File> c = new Comparator<File>() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.6.1
                        @Override // java.util.Comparator
                        public int compare(File lhs, File rhs) {
                            return lhs.getName().compareTo(rhs.getName());
                        }
                    };
                    Collections.sort(dirs, c);
                    Collections.sort(files, c);
                    files.addAll(0, dirs);
                    return;
                }
                if (!file.isFile()) {
                    response.code(404);
                    response.end();
                    return;
                }
                try {
                    FileInputStream is = new FileInputStream(file);
                    response.code(200);
                    Util.pump(is, response, new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServer.6.2
                        @Override // com.koushikdutta.async.callback.CompletedCallback
                        public void onCompleted(Exception ex) {
                            response.end();
                        }
                    });
                } catch (FileNotFoundException e) {
                    response.code(404);
                    response.end();
                }
            }
        });
    }

    public static String getResponseCodeDescription(int code) {
        String d = mCodes.get(Integer.valueOf(code));
        if (d == null) {
            return "Unknown";
        }
        return d;
    }
}
