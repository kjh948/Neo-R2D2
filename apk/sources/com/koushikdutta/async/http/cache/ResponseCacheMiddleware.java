package com.koushikdutta.async.http.cache;

import android.net.Uri;
import android.util.Base64;
import com.koushikdutta.async.AsyncSSLSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.SimpleCancellable;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.SimpleMiddleware;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.util.Charsets;
import com.koushikdutta.async.util.FileCache;
import com.koushikdutta.async.util.StreamUtility;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.CacheResponse;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLEngine;

/* JADX INFO: loaded from: classes.dex */
public class ResponseCacheMiddleware extends SimpleMiddleware {
    public static final String CACHE = "cache";
    public static final String CONDITIONAL_CACHE = "conditional-cache";
    public static final int ENTRY_BODY = 1;
    public static final int ENTRY_COUNT = 2;
    public static final int ENTRY_METADATA = 0;
    private static final String LOGTAG = "AsyncHttpCache";
    public static final String SERVED_FROM = "X-Served-From";
    private FileCache cache;
    private int cacheHitCount;
    private int cacheStoreCount;
    private boolean caching = true;
    private int conditionalCacheHitCount;
    private int networkCount;
    private AsyncServer server;
    private int writeAbortCount;
    private int writeSuccessCount;

    public static class CacheData {
        ResponseHeaders cachedResponseHeaders;
        EntryCacheResponse candidate;
        long contentLength;
        FileInputStream[] snapshot;
    }

    static /* synthetic */ int access$508(ResponseCacheMiddleware x0) {
        int i = x0.writeSuccessCount;
        x0.writeSuccessCount = i + 1;
        return i;
    }

    static /* synthetic */ int access$608(ResponseCacheMiddleware x0) {
        int i = x0.writeAbortCount;
        x0.writeAbortCount = i + 1;
        return i;
    }

    private ResponseCacheMiddleware() {
    }

    public static ResponseCacheMiddleware addCache(AsyncHttpClient client, File cacheDir, long size) throws IOException {
        for (AsyncHttpClientMiddleware middleware : client.getMiddleware()) {
            if (middleware instanceof ResponseCacheMiddleware) {
                throw new IOException("Response cache already added to http client");
            }
        }
        ResponseCacheMiddleware ret = new ResponseCacheMiddleware();
        ret.server = client.getServer();
        ret.cache = new FileCache(cacheDir, size, false);
        client.insertMiddleware(ret);
        return ret;
    }

    public FileCache getFileCache() {
        return this.cache;
    }

    public boolean getCaching() {
        return this.caching;
    }

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public void removeFromCache(Uri uri) {
        String key = FileCache.toKeyString(uri);
        getFileCache().remove(key);
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public Cancellable getSocket(final AsyncHttpClientMiddleware.GetSocketData data) {
        SimpleCancellable ret;
        RequestHeaders requestHeaders = new RequestHeaders(data.request.getUri(), RawHeaders.fromMultimap(data.request.getHeaders().getMultiMap()));
        data.state.put("request-headers", requestHeaders);
        if (this.cache == null || !this.caching || requestHeaders.isNoCache()) {
            this.networkCount++;
            return null;
        }
        String key = FileCache.toKeyString(data.request.getUri());
        FileInputStream[] snapshot = null;
        try {
            snapshot = this.cache.get(key, 2);
            if (snapshot == null) {
                this.networkCount++;
                ret = null;
            } else {
                long contentLength = snapshot[1].available();
                Entry entry = new Entry(snapshot[0]);
                if (!entry.matches(data.request.getUri(), data.request.getMethod(), data.request.getHeaders().getMultiMap())) {
                    this.networkCount++;
                    StreamUtility.closeQuietly(snapshot);
                    ret = null;
                } else {
                    EntryCacheResponse candidate = new EntryCacheResponse(entry, snapshot[1]);
                    try {
                        Map<String, List<String>> responseHeadersMap = candidate.getHeaders();
                        FileInputStream cachedResponseBody = candidate.getBody();
                        if (responseHeadersMap == null || cachedResponseBody == null) {
                            this.networkCount++;
                            StreamUtility.closeQuietly(snapshot);
                            ret = null;
                        } else {
                            RawHeaders rawResponseHeaders = RawHeaders.fromMultimap(responseHeadersMap);
                            ResponseHeaders cachedResponseHeaders = new ResponseHeaders(data.request.getUri(), rawResponseHeaders);
                            rawResponseHeaders.set("Content-Length", String.valueOf(contentLength));
                            rawResponseHeaders.removeAll("Content-Encoding");
                            rawResponseHeaders.removeAll("Transfer-Encoding");
                            cachedResponseHeaders.setLocalTimestamps(System.currentTimeMillis(), System.currentTimeMillis());
                            long now = System.currentTimeMillis();
                            ResponseSource responseSource = cachedResponseHeaders.chooseResponseSource(now, requestHeaders);
                            if (responseSource == ResponseSource.CACHE) {
                                data.request.logi("Response retrieved from cache");
                                CachedSocket socket = entry.isHttps() ? new CachedSSLSocket(candidate, contentLength) : new CachedSocket(candidate, contentLength);
                                socket.pending.add(ByteBuffer.wrap(rawResponseHeaders.toHeaderString().getBytes()));
                                final CachedSocket cachedSocket = socket;
                                this.server.post(new Runnable() { // from class: com.koushikdutta.async.http.cache.ResponseCacheMiddleware.1
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        data.connectCallback.onConnectCompleted(null, cachedSocket);
                                        cachedSocket.sendCachedDataOnNetworkThread();
                                    }
                                });
                                this.cacheHitCount++;
                                data.state.put("socket-owner", this);
                                ret = new SimpleCancellable();
                                ret.setComplete();
                            } else if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
                                data.request.logi("Response may be served from conditional cache");
                                CacheData cacheData = new CacheData();
                                cacheData.snapshot = snapshot;
                                cacheData.contentLength = contentLength;
                                cacheData.cachedResponseHeaders = cachedResponseHeaders;
                                cacheData.candidate = candidate;
                                data.state.put("cache-data", cacheData);
                                ret = null;
                            } else {
                                data.request.logd("Response can not be served from cache");
                                this.networkCount++;
                                StreamUtility.closeQuietly(snapshot);
                                ret = null;
                            }
                        }
                    } catch (Exception e) {
                        this.networkCount++;
                        StreamUtility.closeQuietly(snapshot);
                        ret = null;
                    }
                }
            }
            return ret;
        } catch (IOException e2) {
            this.networkCount++;
            StreamUtility.closeQuietly(snapshot);
            return null;
        }
    }

    public int getConditionalCacheHitCount() {
        return this.conditionalCacheHitCount;
    }

    public int getCacheHitCount() {
        return this.cacheHitCount;
    }

    public int getNetworkCount() {
        return this.networkCount;
    }

    public int getCacheStoreCount() {
        return this.cacheStoreCount;
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onBodyDecoder(AsyncHttpClientMiddleware.OnBodyDataOnRequestSentData data) {
        CachedSocket cached = (CachedSocket) Util.getWrappedSocket(data.socket, CachedSocket.class);
        if (cached != null) {
            data.response.headers().set(SERVED_FROM, CACHE);
            return;
        }
        CacheData cacheData = (CacheData) data.state.get("cache-data");
        RawHeaders rh = RawHeaders.fromMultimap(data.response.headers().getMultiMap());
        rh.removeAll("Content-Length");
        rh.setStatusLine(String.format(Locale.ENGLISH, "%s %s %s", data.response.protocol(), Integer.valueOf(data.response.code()), data.response.message()));
        ResponseHeaders networkResponse = new ResponseHeaders(data.request.getUri(), rh);
        data.state.put("response-headers", networkResponse);
        if (cacheData != null) {
            if (cacheData.cachedResponseHeaders.validate(networkResponse)) {
                data.request.logi("Serving response from conditional cache");
                ResponseHeaders combined = cacheData.cachedResponseHeaders.combine(networkResponse);
                data.response.headers(new Headers(combined.getHeaders().toMultimap()));
                data.response.code(combined.getHeaders().getResponseCode());
                data.response.message(combined.getHeaders().getResponseMessage());
                data.response.headers().set(SERVED_FROM, CONDITIONAL_CACHE);
                this.conditionalCacheHitCount++;
                CachedBodyEmitter bodySpewer = new CachedBodyEmitter(cacheData.candidate, cacheData.contentLength);
                bodySpewer.setDataEmitter(data.bodyEmitter);
                data.bodyEmitter = bodySpewer;
                bodySpewer.sendCachedData();
                return;
            }
            data.state.remove("cache-data");
            StreamUtility.closeQuietly(cacheData.snapshot);
        }
        if (this.caching) {
            RequestHeaders requestHeaders = (RequestHeaders) data.state.get("request-headers");
            if (requestHeaders == null || !networkResponse.isCacheable(requestHeaders) || !data.request.getMethod().equals(AsyncHttpGet.METHOD)) {
                this.networkCount++;
                data.request.logd("Response is not cacheable");
                return;
            }
            String key = FileCache.toKeyString(data.request.getUri());
            RawHeaders varyHeaders = requestHeaders.getHeaders().getAll(networkResponse.getVaryFields());
            Entry entry = new Entry(data.request.getUri(), varyHeaders, data.request, networkResponse.getHeaders());
            BodyCacher cacher = new BodyCacher();
            EntryEditor editor = new EntryEditor(key);
            try {
                entry.writeTo(editor);
                editor.newOutputStream(1);
                cacher.editor = editor;
                cacher.setDataEmitter(data.bodyEmitter);
                data.bodyEmitter = cacher;
                data.state.put("body-cacher", cacher);
                data.request.logd("Caching response");
                this.cacheStoreCount++;
            } catch (Exception e) {
                editor.abort();
                this.networkCount++;
            }
        }
    }

    @Override // com.koushikdutta.async.http.SimpleMiddleware, com.koushikdutta.async.http.AsyncHttpClientMiddleware
    public void onResponseComplete(AsyncHttpClientMiddleware.OnResponseCompleteDataOnRequestSentData data) {
        CacheData cacheData = (CacheData) data.state.get("cache-data");
        if (cacheData != null && cacheData.snapshot != null) {
            StreamUtility.closeQuietly(cacheData.snapshot);
        }
        CachedSocket cachedSocket = (CachedSocket) Util.getWrappedSocket(data.socket, CachedSocket.class);
        if (cachedSocket != null) {
            StreamUtility.closeQuietly(cachedSocket.cacheResponse.getBody());
        }
        BodyCacher cacher = (BodyCacher) data.state.get("body-cacher");
        if (cacher != null) {
            if (data.exception != null) {
                cacher.abort();
            } else {
                cacher.commit();
            }
        }
    }

    public void clear() {
        if (this.cache != null) {
            this.cache.clear();
        }
    }

    private static class BodyCacher extends FilteredDataEmitter {
        ByteBufferList cached;
        EntryEditor editor;

        private BodyCacher() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.koushikdutta.async.DataEmitterBase
        public void report(Exception e) {
            super.report(e);
            if (e != null) {
                abort();
            }
        }

        @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.callback.DataCallback
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            if (this.cached != null) {
                super.onDataAvailable(emitter, this.cached);
                if (this.cached.remaining() <= 0) {
                    this.cached = null;
                } else {
                    return;
                }
            }
            ByteBufferList copy = new ByteBufferList();
            try {
                if (this.editor != null) {
                    OutputStream outputStream = this.editor.newOutputStream(1);
                    if (outputStream != null) {
                        while (!bb.isEmpty()) {
                            ByteBuffer b = bb.remove();
                            try {
                                ByteBufferList.writeOutputStream(outputStream, b);
                            } finally {
                                copy.add(b);
                            }
                        }
                    } else {
                        abort();
                    }
                }
            } catch (Exception e) {
                abort();
            } finally {
                bb.get(copy);
                copy.get(bb);
            }
            super.onDataAvailable(emitter, bb);
            if (this.editor != null && bb.remaining() > 0) {
                this.cached = new ByteBufferList();
                bb.get(this.cached);
            }
        }

        @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
        public void close() {
            abort();
            super.close();
        }

        public void abort() {
            if (this.editor != null) {
                this.editor.abort();
                this.editor = null;
            }
        }

        public void commit() {
            if (this.editor != null) {
                this.editor.commit();
                this.editor = null;
            }
        }
    }

    private static class CachedBodyEmitter extends FilteredDataEmitter {
        static final /* synthetic */ boolean $assertionsDisabled;
        boolean allowEnd;
        EntryCacheResponse cacheResponse;
        private boolean paused;
        ByteBufferList pending = new ByteBufferList();
        private Allocator allocator = new Allocator();
        Runnable sendCachedDataRunnable = new Runnable() { // from class: com.koushikdutta.async.http.cache.ResponseCacheMiddleware.CachedBodyEmitter.1
            @Override // java.lang.Runnable
            public void run() {
                CachedBodyEmitter.this.sendCachedDataOnNetworkThread();
            }
        };

        static {
            $assertionsDisabled = !ResponseCacheMiddleware.class.desiredAssertionStatus();
        }

        public CachedBodyEmitter(EntryCacheResponse cacheResponse, long contentLength) {
            this.cacheResponse = cacheResponse;
            this.allocator.setCurrentAlloc((int) contentLength);
        }

        void sendCachedDataOnNetworkThread() {
            if (this.pending.remaining() > 0) {
                super.onDataAvailable(this, this.pending);
                if (this.pending.remaining() > 0) {
                    return;
                }
            }
            try {
                ByteBuffer buffer = this.allocator.allocate();
                if (!$assertionsDisabled && buffer.position() != 0) {
                    throw new AssertionError();
                }
                FileInputStream din = this.cacheResponse.getBody();
                int read = din.read(buffer.array(), buffer.arrayOffset(), buffer.capacity());
                if (read == -1) {
                    ByteBufferList.reclaim(buffer);
                    this.allowEnd = true;
                    report(null);
                    return;
                }
                this.allocator.track(read);
                buffer.limit(read);
                this.pending.add(buffer);
                super.onDataAvailable(this, this.pending);
                if (this.pending.remaining() <= 0) {
                    getServer().postDelayed(this.sendCachedDataRunnable, 10L);
                }
            } catch (IOException e) {
                this.allowEnd = true;
                report(e);
            }
        }

        void sendCachedData() {
            getServer().post(this.sendCachedDataRunnable);
        }

        @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
        public void resume() {
            this.paused = false;
            sendCachedData();
        }

        @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
        public boolean isPaused() {
            return this.paused;
        }

        @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
        public void close() {
            if (getServer().getAffinity() != Thread.currentThread()) {
                getServer().post(new Runnable() { // from class: com.koushikdutta.async.http.cache.ResponseCacheMiddleware.CachedBodyEmitter.2
                    @Override // java.lang.Runnable
                    public void run() {
                        CachedBodyEmitter.this.close();
                    }
                });
                return;
            }
            this.pending.recycle();
            StreamUtility.closeQuietly(this.cacheResponse.getBody());
            super.close();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.koushikdutta.async.DataEmitterBase
        public void report(Exception e) {
            if (this.allowEnd) {
                StreamUtility.closeQuietly(this.cacheResponse.getBody());
                super.report(e);
            }
        }
    }

    private static final class Entry {
        private final String cipherSuite;
        private final Certificate[] localCertificates;
        private final Certificate[] peerCertificates;
        private final String requestMethod;
        private final RawHeaders responseHeaders;
        private final String uri;
        private final RawHeaders varyHeaders;

        public Entry(InputStream in) throws Throwable {
            StrictLineReader reader = null;
            try {
                StrictLineReader reader2 = new StrictLineReader(in, Charsets.US_ASCII);
                try {
                    this.uri = reader2.readLine();
                    this.requestMethod = reader2.readLine();
                    this.varyHeaders = new RawHeaders();
                    int varyRequestHeaderLineCount = reader2.readInt();
                    for (int i = 0; i < varyRequestHeaderLineCount; i++) {
                        this.varyHeaders.addLine(reader2.readLine());
                    }
                    this.responseHeaders = new RawHeaders();
                    this.responseHeaders.setStatusLine(reader2.readLine());
                    int responseHeaderLineCount = reader2.readInt();
                    for (int i2 = 0; i2 < responseHeaderLineCount; i2++) {
                        this.responseHeaders.addLine(reader2.readLine());
                    }
                    this.cipherSuite = null;
                    this.peerCertificates = null;
                    this.localCertificates = null;
                    StreamUtility.closeQuietly(reader2, in);
                } catch (Throwable th) {
                    th = th;
                    reader = reader2;
                    StreamUtility.closeQuietly(reader, in);
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
            }
        }

        public Entry(Uri uri, RawHeaders varyHeaders, AsyncHttpRequest request, RawHeaders responseHeaders) {
            this.uri = uri.toString();
            this.varyHeaders = varyHeaders;
            this.requestMethod = request.getMethod();
            this.responseHeaders = responseHeaders;
            this.cipherSuite = null;
            this.peerCertificates = null;
            this.localCertificates = null;
        }

        public void writeTo(EntryEditor editor) throws IOException {
            OutputStream out = editor.newOutputStream(0);
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, Charsets.UTF_8));
            writer.write(this.uri + '\n');
            writer.write(this.requestMethod + '\n');
            writer.write(Integer.toString(this.varyHeaders.length()) + '\n');
            for (int i = 0; i < this.varyHeaders.length(); i++) {
                writer.write(this.varyHeaders.getFieldName(i) + ": " + this.varyHeaders.getValue(i) + '\n');
            }
            writer.write(this.responseHeaders.getStatusLine() + '\n');
            writer.write(Integer.toString(this.responseHeaders.length()) + '\n');
            for (int i2 = 0; i2 < this.responseHeaders.length(); i2++) {
                writer.write(this.responseHeaders.getFieldName(i2) + ": " + this.responseHeaders.getValue(i2) + '\n');
            }
            if (isHttps()) {
                writer.write(10);
                writer.write(this.cipherSuite + '\n');
                writeCertArray(writer, this.peerCertificates);
                writeCertArray(writer, this.localCertificates);
            }
            writer.close();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean isHttps() {
            return this.uri.startsWith("https://");
        }

        private Certificate[] readCertArray(StrictLineReader reader) throws IOException {
            int length = reader.readInt();
            if (length == -1) {
                return null;
            }
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate[] result = new Certificate[length];
                for (int i = 0; i < result.length; i++) {
                    String line = reader.readLine();
                    byte[] bytes = Base64.decode(line, 0);
                    result[i] = certificateFactory.generateCertificate(new ByteArrayInputStream(bytes));
                }
                return result;
            } catch (CertificateException e) {
                throw new IOException(e.getMessage());
            }
        }

        private void writeCertArray(Writer writer, Certificate[] certificates) throws IOException {
            if (certificates == null) {
                writer.write("-1\n");
                return;
            }
            try {
                writer.write(Integer.toString(certificates.length) + '\n');
                for (Certificate certificate : certificates) {
                    byte[] bytes = certificate.getEncoded();
                    String line = Base64.encodeToString(bytes, 0);
                    writer.write(line + '\n');
                }
            } catch (CertificateEncodingException e) {
                throw new IOException(e.getMessage());
            }
        }

        public boolean matches(Uri uri, String requestMethod, Map<String, List<String>> requestHeaders) {
            return this.uri.equals(uri.toString()) && this.requestMethod.equals(requestMethod) && new ResponseHeaders(uri, this.responseHeaders).varyMatches(this.varyHeaders.toMultimap(), requestHeaders);
        }
    }

    static class EntryCacheResponse extends CacheResponse {
        private final Entry entry;
        private final FileInputStream snapshot;

        public EntryCacheResponse(Entry entry, FileInputStream snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
        }

        @Override // java.net.CacheResponse
        public Map<String, List<String>> getHeaders() {
            return this.entry.responseHeaders.toMultimap();
        }

        @Override // java.net.CacheResponse
        public FileInputStream getBody() {
            return this.snapshot;
        }
    }

    private class CachedSSLSocket extends CachedSocket implements AsyncSSLSocket {
        public CachedSSLSocket(EntryCacheResponse cacheResponse, long contentLength) {
            super(cacheResponse, contentLength);
        }

        @Override // com.koushikdutta.async.AsyncSSLSocket
        public SSLEngine getSSLEngine() {
            return null;
        }

        @Override // com.koushikdutta.async.AsyncSSLSocket
        public X509Certificate[] getPeerCertificates() {
            return null;
        }
    }

    private class CachedSocket extends CachedBodyEmitter implements AsyncSocket {
        boolean closed;
        CompletedCallback closedCallback;
        boolean open;

        public CachedSocket(EntryCacheResponse cacheResponse, long contentLength) {
            super(cacheResponse, contentLength);
            this.allowEnd = true;
        }

        @Override // com.koushikdutta.async.DataSink
        public void end() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // com.koushikdutta.async.http.cache.ResponseCacheMiddleware.CachedBodyEmitter, com.koushikdutta.async.DataEmitterBase
        public void report(Exception e) {
            super.report(e);
            if (!this.closed) {
                this.closed = true;
                if (this.closedCallback != null) {
                    this.closedCallback.onCompleted(e);
                }
            }
        }

        @Override // com.koushikdutta.async.DataSink
        public void write(ByteBufferList bb) {
            bb.recycle();
        }

        @Override // com.koushikdutta.async.DataSink
        public WritableCallback getWriteableCallback() {
            return null;
        }

        @Override // com.koushikdutta.async.DataSink
        public void setWriteableCallback(WritableCallback handler) {
        }

        @Override // com.koushikdutta.async.DataSink
        public boolean isOpen() {
            return this.open;
        }

        @Override // com.koushikdutta.async.http.cache.ResponseCacheMiddleware.CachedBodyEmitter, com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter
        public void close() {
            this.open = false;
        }

        @Override // com.koushikdutta.async.DataSink
        public CompletedCallback getClosedCallback() {
            return this.closedCallback;
        }

        @Override // com.koushikdutta.async.DataSink
        public void setClosedCallback(CompletedCallback handler) {
            this.closedCallback = handler;
        }

        @Override // com.koushikdutta.async.FilteredDataEmitter, com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
        public AsyncServer getServer() {
            return ResponseCacheMiddleware.this.server;
        }
    }

    class EntryEditor {
        boolean done;
        String key;
        FileOutputStream[] outs = new FileOutputStream[2];
        File[] temps;

        public EntryEditor(String key) {
            this.key = key;
            this.temps = ResponseCacheMiddleware.this.cache.getTempFiles(2);
        }

        void commit() {
            StreamUtility.closeQuietly(this.outs);
            if (!this.done) {
                ResponseCacheMiddleware.this.cache.commitTempFiles(this.key, this.temps);
                ResponseCacheMiddleware.access$508(ResponseCacheMiddleware.this);
                this.done = true;
            }
        }

        FileOutputStream newOutputStream(int index) throws IOException {
            if (this.outs[index] == null) {
                this.outs[index] = new FileOutputStream(this.temps[index]);
            }
            return this.outs[index];
        }

        void abort() {
            StreamUtility.closeQuietly(this.outs);
            FileCache.removeFiles(this.temps);
            if (!this.done) {
                ResponseCacheMiddleware.access$608(ResponseCacheMiddleware.this);
                this.done = true;
            }
        }
    }
}
