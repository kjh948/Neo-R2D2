package com.koushikdutta.async.http.server;

import android.support.v7.widget.ActivityChooserView;
import android.text.TextUtils;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.http.AsyncHttpHead;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.HttpUtil;
import com.koushikdutta.async.http.Protocol;
import com.koushikdutta.async.http.filter.ChunkedOutputFilter;
import com.koushikdutta.async.util.StreamUtility;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public class AsyncHttpServerResponseImpl implements AsyncHttpServerResponse {
    static final /* synthetic */ boolean $assertionsDisabled;
    CompletedCallback closedCallback;
    boolean ended;
    boolean mEnded;
    AsyncHttpServerRequestImpl mRequest;
    DataSink mSink;
    AsyncSocket mSocket;
    WritableCallback writable;
    private Headers mRawHeaders = new Headers();
    private long mContentLength = -1;
    boolean headWritten = false;
    int code = 200;

    static {
        $assertionsDisabled = !AsyncHttpServerResponseImpl.class.desiredAssertionStatus();
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public Headers getHeaders() {
        return this.mRawHeaders;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public AsyncSocket getSocket() {
        return this.mSocket;
    }

    AsyncHttpServerResponseImpl(AsyncSocket socket, AsyncHttpServerRequestImpl req) {
        this.mSocket = socket;
        this.mRequest = req;
        if (HttpUtil.isKeepAlive(Protocol.HTTP_1_1, req.getHeaders())) {
            this.mRawHeaders.set("Connection", "Keep-Alive");
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void write(ByteBufferList bb) {
        if (!$assertionsDisabled && this.mEnded) {
            throw new AssertionError();
        }
        if (!this.headWritten) {
            initFirstWrite();
        }
        if (bb.remaining() != 0 && this.mSink != null) {
            this.mSink.write(bb);
        }
    }

    void initFirstWrite() {
        final boolean isChunked;
        if (!this.headWritten) {
            this.headWritten = true;
            String currentEncoding = this.mRawHeaders.get("Transfer-Encoding");
            if ("".equals(currentEncoding)) {
                this.mRawHeaders.removeAll("Transfer-Encoding");
            }
            boolean canUseChunked = ("Chunked".equalsIgnoreCase(currentEncoding) || currentEncoding == null) && !"close".equalsIgnoreCase(this.mRawHeaders.get("Connection"));
            if (this.mContentLength < 0) {
                String contentLength = this.mRawHeaders.get("Content-Length");
                if (!TextUtils.isEmpty(contentLength)) {
                    this.mContentLength = Long.valueOf(contentLength).longValue();
                }
            }
            if (this.mContentLength < 0 && canUseChunked) {
                this.mRawHeaders.set("Transfer-Encoding", "Chunked");
                isChunked = true;
            } else {
                isChunked = false;
            }
            String statusLine = String.format(Locale.ENGLISH, "HTTP/1.1 %s %s", Integer.valueOf(this.code), AsyncHttpServer.getResponseCodeDescription(this.code));
            String rh = this.mRawHeaders.toPrefixString(statusLine);
            Util.writeAll(this.mSocket, rh.getBytes(), new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl.1
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    if (ex != null) {
                        AsyncHttpServerResponseImpl.this.report(ex);
                        return;
                    }
                    if (isChunked) {
                        ChunkedOutputFilter chunked = new ChunkedOutputFilter(AsyncHttpServerResponseImpl.this.mSocket);
                        chunked.setMaxBuffer(0);
                        AsyncHttpServerResponseImpl.this.mSink = chunked;
                    } else {
                        AsyncHttpServerResponseImpl.this.mSink = AsyncHttpServerResponseImpl.this.mSocket;
                    }
                    AsyncHttpServerResponseImpl.this.mSink.setClosedCallback(AsyncHttpServerResponseImpl.this.closedCallback);
                    AsyncHttpServerResponseImpl.this.closedCallback = null;
                    AsyncHttpServerResponseImpl.this.mSink.setWriteableCallback(AsyncHttpServerResponseImpl.this.writable);
                    AsyncHttpServerResponseImpl.this.writable = null;
                    if (AsyncHttpServerResponseImpl.this.ended) {
                        AsyncHttpServerResponseImpl.this.end();
                    } else {
                        AsyncHttpServerResponseImpl.this.getServer().post(new Runnable() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl.1.1
                            @Override // java.lang.Runnable
                            public void run() {
                                WritableCallback wb = AsyncHttpServerResponseImpl.this.getWriteableCallback();
                                if (wb != null) {
                                    wb.onWriteable();
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void setWriteableCallback(WritableCallback handler) {
        if (this.mSink != null) {
            this.mSink.setWriteableCallback(handler);
        } else {
            this.writable = handler;
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public WritableCallback getWriteableCallback() {
        return this.mSink != null ? this.mSink.getWriteableCallback() : this.writable;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse, com.koushikdutta.async.DataSink
    public void end() {
        if (!this.ended) {
            this.ended = true;
            if (!this.headWritten || this.mSink != null) {
                if (!this.headWritten) {
                    this.mRawHeaders.remove("Transfer-Encoding");
                }
                if (this.mSink instanceof ChunkedOutputFilter) {
                    ((ChunkedOutputFilter) this.mSink).setMaxBuffer(ActivityChooserView.ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
                    this.mSink.write(new ByteBufferList());
                    onEnd();
                } else {
                    if (!this.headWritten) {
                        if (!this.mRequest.getMethod().equalsIgnoreCase(AsyncHttpHead.METHOD)) {
                            send("text/html", "");
                            return;
                        } else {
                            writeHead();
                            onEnd();
                            return;
                        }
                    }
                    onEnd();
                }
            }
        }
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void writeHead() {
        initFirstWrite();
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void setContentType(String contentType) {
        this.mRawHeaders.set("Content-Type", contentType);
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void send(String contentType, byte[] bytes) {
        if (!$assertionsDisabled && this.mContentLength >= 0) {
            throw new AssertionError();
        }
        this.mContentLength = bytes.length;
        this.mRawHeaders.set("Content-Length", Integer.toString(bytes.length));
        this.mRawHeaders.set("Content-Type", contentType);
        Util.writeAll(this, bytes, new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl.2
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                AsyncHttpServerResponseImpl.this.onEnd();
            }
        });
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void send(String contentType, String string) {
        try {
            send(contentType, string.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    protected void onEnd() {
        this.mEnded = true;
    }

    protected void report(Exception e) {
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void send(String string) {
        String contentType = this.mRawHeaders.get("Content-Type");
        if (contentType == null) {
            contentType = "text/html; charset=utf-8";
        }
        send(contentType, string);
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void send(JSONObject json) {
        send("application/json; charset=utf-8", json.toString());
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void sendStream(final InputStream inputStream, long totalLength) {
        long start = 0;
        long end = totalLength - 1;
        String range = this.mRequest.getHeaders().get("Range");
        if (range != null) {
            String[] parts = range.split("=");
            if (parts.length != 2 || !"bytes".equals(parts[0])) {
                code(416);
                end();
                return;
            }
            String[] parts2 = parts[1].split("-");
            try {
                if (parts2.length > 2) {
                    throw new MalformedRangeException();
                }
                if (!TextUtils.isEmpty(parts2[0])) {
                    start = Long.parseLong(parts2[0]);
                }
                if (parts2.length == 2 && !TextUtils.isEmpty(parts2[1])) {
                    end = Long.parseLong(parts2[1]);
                } else {
                    end = totalLength - 1;
                }
                code(206);
                getHeaders().set("Content-Range", String.format(Locale.ENGLISH, "bytes %d-%d/%d", Long.valueOf(start), Long.valueOf(end), Long.valueOf(totalLength)));
            } catch (Exception e) {
                code(416);
                end();
                return;
            }
        }
        try {
            if (start != inputStream.skip(start)) {
                throw new StreamSkipException("skip failed to skip requested amount");
            }
            this.mContentLength = (end - start) + 1;
            this.mRawHeaders.set("Content-Length", String.valueOf(this.mContentLength));
            this.mRawHeaders.set("Accept-Ranges", "bytes");
            if (this.mRequest.getMethod().equals(AsyncHttpHead.METHOD)) {
                writeHead();
                onEnd();
            } else {
                Util.pump(inputStream, this.mContentLength, this, new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl.3
                    @Override // com.koushikdutta.async.callback.CompletedCallback
                    public void onCompleted(Exception ex) {
                        StreamUtility.closeQuietly(inputStream);
                        AsyncHttpServerResponseImpl.this.onEnd();
                    }
                });
            }
        } catch (Exception e2) {
            code(500);
            end();
        }
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void sendFile(File file) {
        try {
            if (this.mRawHeaders.get("Content-Type") == null) {
                this.mRawHeaders.set("Content-Type", AsyncHttpServer.getContentType(file.getAbsolutePath()));
            }
            FileInputStream fin = new FileInputStream(file);
            sendStream(new BufferedInputStream(fin, 64000), file.length());
        } catch (FileNotFoundException e) {
            code(404);
            end();
        }
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void proxy(final AsyncHttpResponse remoteResponse) {
        code(remoteResponse.code());
        remoteResponse.headers().removeAll("Transfer-Encoding");
        remoteResponse.headers().removeAll("Content-Encoding");
        remoteResponse.headers().removeAll("Connection");
        getHeaders().addAll(remoteResponse.headers());
        remoteResponse.headers().set("Connection", "close");
        Util.pump(remoteResponse, this, new CompletedCallback() { // from class: com.koushikdutta.async.http.server.AsyncHttpServerResponseImpl.4
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                remoteResponse.setEndCallback(new CompletedCallback.NullCompletedCallback());
                remoteResponse.setDataCallback(new DataCallback.NullDataCallback());
                AsyncHttpServerResponseImpl.this.end();
            }
        });
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public AsyncHttpServerResponse code(int code) {
        this.code = code;
        return this;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public int code() {
        return this.code;
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse
    public void redirect(String location) {
        code(302);
        this.mRawHeaders.set("Location", location);
        end();
    }

    @Override // com.koushikdutta.async.http.server.AsyncHttpServerResponse, com.koushikdutta.async.callback.CompletedCallback
    public void onCompleted(Exception ex) {
        end();
    }

    @Override // com.koushikdutta.async.DataSink
    public boolean isOpen() {
        return this.mSink != null ? this.mSink.isOpen() : this.mSocket.isOpen();
    }

    @Override // com.koushikdutta.async.DataSink
    public void setClosedCallback(CompletedCallback handler) {
        if (this.mSink != null) {
            this.mSink.setClosedCallback(handler);
        } else {
            this.closedCallback = handler;
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public CompletedCallback getClosedCallback() {
        return this.mSink != null ? this.mSink.getClosedCallback() : this.closedCallback;
    }

    @Override // com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mSocket.getServer();
    }

    public String toString() {
        if (this.mRawHeaders == null) {
            return super.toString();
        }
        String statusLine = String.format(Locale.ENGLISH, "HTTP/1.1 %s %s", Integer.valueOf(this.code), AsyncHttpServer.getResponseCodeDescription(this.code));
        return this.mRawHeaders.toPrefixString(statusLine);
    }
}
