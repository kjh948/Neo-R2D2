package com.koushikdutta.async.http;

import android.text.TextUtils;
import android.util.Base64;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.BufferedDataSink;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.UUID;

/* JADX INFO: loaded from: classes.dex */
public class WebSocketImpl implements WebSocket {
    static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private DataCallback mDataCallback;
    CompletedCallback mExceptionCallback;
    HybiParser mParser;
    private WebSocket.PingCallback mPingCallback;
    private WebSocket.PongCallback mPongCallback;
    BufferedDataSink mSink;
    private AsyncSocket mSocket;
    private WebSocket.StringCallback mStringCallback;
    private LinkedList<ByteBufferList> pending;

    @Override // com.koushikdutta.async.DataSink
    public void end() {
        this.mSocket.end();
    }

    private static byte[] toByteArray(UUID uuid) {
        byte[] byteArray = new byte[16];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        LongBuffer longBuffer = buffer.asLongBuffer();
        longBuffer.put(new long[]{uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()});
        return byteArray;
    }

    private static String SHA1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            byte[] sha1hash = md.digest();
            return Base64.encodeToString(sha1hash, 2);
        } catch (Exception e) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addAndEmit(ByteBufferList bb) {
        if (this.pending == null) {
            Util.emitAllData(this, bb);
            if (bb.remaining() > 0) {
                this.pending = new LinkedList<>();
                this.pending.add(bb);
                return;
            }
            return;
        }
        while (!isPaused()) {
            ByteBufferList bb2 = this.pending.remove();
            Util.emitAllData(this, bb2);
            if (bb2.remaining() > 0) {
                this.pending.add(0, bb2);
            }
        }
        if (this.pending.size() == 0) {
            this.pending = null;
        }
    }

    private void setupParser(boolean masking, boolean deflate) {
        this.mParser = new HybiParser(this.mSocket) { // from class: com.koushikdutta.async.http.WebSocketImpl.1
            @Override // com.koushikdutta.async.http.HybiParser
            protected void report(Exception ex) {
                if (WebSocketImpl.this.mExceptionCallback != null) {
                    WebSocketImpl.this.mExceptionCallback.onCompleted(ex);
                }
            }

            @Override // com.koushikdutta.async.http.HybiParser
            protected void onMessage(byte[] payload) {
                WebSocketImpl.this.addAndEmit(new ByteBufferList(payload));
            }

            @Override // com.koushikdutta.async.http.HybiParser
            protected void onMessage(String payload) {
                if (WebSocketImpl.this.mStringCallback != null) {
                    WebSocketImpl.this.mStringCallback.onStringAvailable(payload);
                }
            }

            @Override // com.koushikdutta.async.http.HybiParser
            protected void onDisconnect(int code, String reason) {
                WebSocketImpl.this.mSocket.close();
            }

            @Override // com.koushikdutta.async.http.HybiParser
            protected void sendFrame(byte[] frame) {
                WebSocketImpl.this.mSink.write(new ByteBufferList(frame));
            }

            @Override // com.koushikdutta.async.http.HybiParser
            protected void onPing(String payload) {
                if (WebSocketImpl.this.mPingCallback != null) {
                    WebSocketImpl.this.mPingCallback.onPingReceived(payload);
                }
            }

            @Override // com.koushikdutta.async.http.HybiParser
            protected void onPong(String payload) {
                if (WebSocketImpl.this.mPongCallback != null) {
                    WebSocketImpl.this.mPongCallback.onPongReceived(payload);
                }
            }
        };
        this.mParser.setMasking(masking);
        this.mParser.setDeflate(deflate);
        if (this.mSocket.isPaused()) {
            this.mSocket.resume();
        }
    }

    public WebSocketImpl(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
        this(request.getSocket());
        String key = request.getHeaders().get("Sec-WebSocket-Key");
        String concat = key + MAGIC;
        String sha1 = SHA1(concat);
        request.getHeaders().get("Origin");
        response.code(101);
        response.getHeaders().set("Upgrade", "WebSocket");
        response.getHeaders().set("Connection", "Upgrade");
        response.getHeaders().set("Sec-WebSocket-Accept", sha1);
        String protocol = request.getHeaders().get("Sec-WebSocket-Protocol");
        if (!TextUtils.isEmpty(protocol)) {
            response.getHeaders().set("Sec-WebSocket-Protocol", protocol);
        }
        response.writeHead();
        setupParser(false, false);
    }

    public static void addWebSocketUpgradeHeaders(AsyncHttpRequest req, String protocol) {
        Headers headers = req.getHeaders();
        String key = Base64.encodeToString(toByteArray(UUID.randomUUID()), 2);
        headers.set("Sec-WebSocket-Version", "13");
        headers.set("Sec-WebSocket-Key", key);
        headers.set("Sec-WebSocket-Extensions", "x-webkit-deflate-frame");
        headers.set("Connection", "Upgrade");
        headers.set("Upgrade", "websocket");
        if (protocol != null) {
            headers.set("Sec-WebSocket-Protocol", protocol);
        }
        headers.set("Pragma", "no-cache");
        headers.set("Cache-Control", "no-cache");
        if (TextUtils.isEmpty(req.getHeaders().get("User-Agent"))) {
            req.getHeaders().set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.15 Safari/537.36");
        }
    }

    public WebSocketImpl(AsyncSocket socket) {
        this.mSocket = socket;
        this.mSink = new BufferedDataSink(this.mSocket);
    }

    public static WebSocket finishHandshake(Headers requestHeaders, AsyncHttpResponse response) {
        String sha1;
        String key;
        if (response == null || response.code() != 101 || !"websocket".equalsIgnoreCase(response.headers().get("Upgrade")) || (sha1 = response.headers().get("Sec-WebSocket-Accept")) == null || (key = requestHeaders.get("Sec-WebSocket-Key")) == null) {
            return null;
        }
        String concat = key + MAGIC;
        String expected = SHA1(concat).trim();
        if (!sha1.equalsIgnoreCase(expected)) {
            return null;
        }
        String extensions = requestHeaders.get("Sec-WebSocket-Extensions");
        boolean deflate = false;
        if (extensions != null && extensions.equals("x-webkit-deflate-frame")) {
            deflate = true;
        }
        WebSocketImpl ret = new WebSocketImpl(response.detachSocket());
        ret.setupParser(true, deflate);
        return ret;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void close() {
        this.mSocket.close();
    }

    @Override // com.koushikdutta.async.DataSink
    public void setClosedCallback(CompletedCallback handler) {
        this.mSocket.setClosedCallback(handler);
    }

    @Override // com.koushikdutta.async.DataSink
    public CompletedCallback getClosedCallback() {
        return this.mSocket.getClosedCallback();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void setEndCallback(CompletedCallback callback) {
        this.mExceptionCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public CompletedCallback getEndCallback() {
        return this.mExceptionCallback;
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void send(byte[] bytes) {
        this.mSink.write(new ByteBufferList(this.mParser.frame(bytes)));
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void send(byte[] bytes, int offset, int len) {
        this.mSink.write(new ByteBufferList(this.mParser.frame(bytes, offset, len)));
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void send(String string) {
        this.mSink.write(new ByteBufferList(this.mParser.frame(string)));
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void ping(String string) {
        this.mSink.write(new ByteBufferList(ByteBuffer.wrap(this.mParser.pingFrame(string))));
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void pong(String string) {
        this.mSink.write(new ByteBufferList(ByteBuffer.wrap(this.mParser.pongFrame(string))));
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void setStringCallback(WebSocket.StringCallback callback) {
        this.mStringCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void setDataCallback(DataCallback callback) {
        this.mDataCallback = callback;
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public WebSocket.StringCallback getStringCallback() {
        return this.mStringCallback;
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void setPingCallback(WebSocket.PingCallback callback) {
        this.mPingCallback = callback;
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public void setPongCallback(WebSocket.PongCallback callback) {
        this.mPongCallback = callback;
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public WebSocket.PongCallback getPongCallback() {
        return this.mPongCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public DataCallback getDataCallback() {
        return this.mDataCallback;
    }

    @Override // com.koushikdutta.async.DataSink
    public boolean isOpen() {
        return this.mSocket.isOpen();
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public boolean isBuffering() {
        return this.mSink.remaining() > 0;
    }

    @Override // com.koushikdutta.async.DataSink
    public void write(ByteBufferList bb) {
        byte[] buf = bb.getAllByteArray();
        send(buf);
    }

    @Override // com.koushikdutta.async.DataSink
    public void setWriteableCallback(WritableCallback handler) {
        this.mSink.setWriteableCallback(handler);
    }

    @Override // com.koushikdutta.async.DataSink
    public WritableCallback getWriteableCallback() {
        return this.mSink.getWriteableCallback();
    }

    @Override // com.koushikdutta.async.http.WebSocket
    public AsyncSocket getSocket() {
        return this.mSocket;
    }

    @Override // com.koushikdutta.async.AsyncSocket, com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mSocket.getServer();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isChunked() {
        return false;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void pause() {
        this.mSocket.pause();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void resume() {
        this.mSocket.resume();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isPaused() {
        return this.mSocket.isPaused();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public String charset() {
        return null;
    }
}
