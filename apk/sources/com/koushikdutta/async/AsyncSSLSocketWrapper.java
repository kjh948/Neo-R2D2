package com.koushikdutta.async;

import android.os.Build;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.http.conn.ssl.StrictHostnameVerifier;

/* JADX INFO: loaded from: classes.dex */
public class AsyncSSLSocketWrapper implements AsyncSocketWrapper, AsyncSSLSocket {
    static final /* synthetic */ boolean $assertionsDisabled;
    static SSLContext defaultSSLContext;
    boolean clientMode;
    SSLEngine engine;
    boolean finishedHandshake;
    HandshakeCallback handshakeCallback;
    HostnameVerifier hostnameVerifier;
    DataCallback mDataCallback;
    CompletedCallback mEndCallback;
    Exception mEndException;
    boolean mEnded;
    private String mHost;
    private int mPort;
    BufferedDataSink mSink;
    AsyncSocket mSocket;
    boolean mUnwrapping;
    private boolean mWrapping;
    WritableCallback mWriteableCallback;
    X509Certificate[] peerCertificates;
    TrustManager[] trustManagers;
    final ByteBufferList pending = new ByteBufferList();
    final DataCallback dataCallback = new DataCallback() { // from class: com.koushikdutta.async.AsyncSSLSocketWrapper.5
        final Allocator allocator = new Allocator().setMinAlloc(8192);
        final ByteBufferList buffered = new ByteBufferList();

        @Override // com.koushikdutta.async.callback.DataCallback
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            try {
                if (!AsyncSSLSocketWrapper.this.mUnwrapping) {
                    AsyncSSLSocketWrapper.this.mUnwrapping = true;
                    bb.get(this.buffered);
                    if (this.buffered.hasRemaining()) {
                        ByteBuffer all = this.buffered.getAll();
                        this.buffered.add(all);
                    }
                    ByteBuffer b = ByteBufferList.EMPTY_BYTEBUFFER;
                    while (true) {
                        if (b.remaining() == 0 && this.buffered.size() > 0) {
                            b = this.buffered.remove();
                        }
                        int remaining = b.remaining();
                        int before = AsyncSSLSocketWrapper.this.pending.remaining();
                        ByteBuffer readBuf = this.allocator.allocate();
                        SSLEngineResult res = AsyncSSLSocketWrapper.this.engine.unwrap(b, readBuf);
                        AsyncSSLSocketWrapper.this.addToPending(AsyncSSLSocketWrapper.this.pending, readBuf);
                        this.allocator.track(AsyncSSLSocketWrapper.this.pending.remaining() - before);
                        if (res.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                            this.allocator.setMinAlloc(this.allocator.getMinAlloc() * 2);
                            remaining = -1;
                        } else if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            this.buffered.addFirst(b);
                            if (this.buffered.size() <= 1) {
                                break;
                            }
                            remaining = -1;
                            ByteBuffer b2 = this.buffered.getAll();
                            this.buffered.addFirst(b2);
                            b = ByteBufferList.EMPTY_BYTEBUFFER;
                        }
                        AsyncSSLSocketWrapper.this.handleHandshakeStatus(res.getHandshakeStatus());
                        if (b.remaining() == remaining && before == AsyncSSLSocketWrapper.this.pending.remaining()) {
                            this.buffered.addFirst(b);
                            break;
                        }
                    }
                    AsyncSSLSocketWrapper.this.onDataAvailable();
                }
            } catch (SSLException ex) {
                ex.printStackTrace();
                AsyncSSLSocketWrapper.this.report(ex);
            } finally {
                AsyncSSLSocketWrapper.this.mUnwrapping = false;
            }
        }
    };
    ByteBufferList writeList = new ByteBufferList();

    public interface HandshakeCallback {
        void onHandshakeCompleted(Exception exc, AsyncSSLSocket asyncSSLSocket);
    }

    static {
        $assertionsDisabled = AsyncSSLSocketWrapper.class.desiredAssertionStatus() ? false : true;
        try {
            if (Build.VERSION.SDK_INT <= 15) {
                throw new Exception();
            }
            defaultSSLContext = SSLContext.getInstance("Default");
        } catch (Exception ex) {
            try {
                defaultSSLContext = SSLContext.getInstance("TLS");
                TrustManager[] trustAllCerts = {new X509TrustManager() { // from class: com.koushikdutta.async.AsyncSSLSocketWrapper.1
                    @Override // javax.net.ssl.X509TrustManager
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override // javax.net.ssl.X509TrustManager
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override // javax.net.ssl.X509TrustManager
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        for (X509Certificate cert : certs) {
                            if (cert != null && cert.getCriticalExtensionOIDs() != null) {
                                cert.getCriticalExtensionOIDs().remove("2.5.29.15");
                            }
                        }
                    }
                }};
                defaultSSLContext.init(null, trustAllCerts, null);
            } catch (Exception ex2) {
                ex.printStackTrace();
                ex2.printStackTrace();
            }
        }
    }

    public static SSLContext getDefaultSSLContext() {
        return defaultSSLContext;
    }

    public static void handshake(AsyncSocket socket, String host, int port, SSLEngine sslEngine, TrustManager[] trustManagers, HostnameVerifier verifier, boolean clientMode, final HandshakeCallback callback) {
        AsyncSSLSocketWrapper wrapper = new AsyncSSLSocketWrapper(socket, host, port, sslEngine, trustManagers, verifier, clientMode);
        wrapper.handshakeCallback = callback;
        socket.setClosedCallback(new CompletedCallback() { // from class: com.koushikdutta.async.AsyncSSLSocketWrapper.2
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    callback.onHandshakeCompleted(ex, null);
                } else {
                    callback.onHandshakeCompleted(new SSLException("socket closed during handshake"), null);
                }
            }
        });
        try {
            wrapper.engine.beginHandshake();
            wrapper.handleHandshakeStatus(wrapper.engine.getHandshakeStatus());
        } catch (SSLException e) {
            wrapper.report(e);
        }
    }

    private AsyncSSLSocketWrapper(AsyncSocket socket, String host, int port, SSLEngine sslEngine, TrustManager[] trustManagers, HostnameVerifier verifier, boolean clientMode) {
        this.mSocket = socket;
        this.hostnameVerifier = verifier;
        this.clientMode = clientMode;
        this.trustManagers = trustManagers;
        this.engine = sslEngine;
        this.mHost = host;
        this.mPort = port;
        this.engine.setUseClientMode(clientMode);
        this.mSink = new BufferedDataSink(socket);
        this.mSink.setWriteableCallback(new WritableCallback() { // from class: com.koushikdutta.async.AsyncSSLSocketWrapper.3
            @Override // com.koushikdutta.async.callback.WritableCallback
            public void onWriteable() {
                if (AsyncSSLSocketWrapper.this.mWriteableCallback != null) {
                    AsyncSSLSocketWrapper.this.mWriteableCallback.onWriteable();
                }
            }
        });
        this.mSocket.setEndCallback(new CompletedCallback() { // from class: com.koushikdutta.async.AsyncSSLSocketWrapper.4
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (!AsyncSSLSocketWrapper.this.mEnded) {
                    AsyncSSLSocketWrapper.this.mEnded = true;
                    AsyncSSLSocketWrapper.this.mEndException = ex;
                    if (!AsyncSSLSocketWrapper.this.pending.hasRemaining() && AsyncSSLSocketWrapper.this.mEndCallback != null) {
                        AsyncSSLSocketWrapper.this.mEndCallback.onCompleted(ex);
                    }
                }
            }
        });
        this.mSocket.setDataCallback(this.dataCallback);
    }

    public void onDataAvailable() {
        Util.emitAllData(this, this.pending);
        if (this.mEnded && !this.pending.hasRemaining() && this.mEndCallback != null) {
            this.mEndCallback.onCompleted(this.mEndException);
        }
    }

    @Override // com.koushikdutta.async.AsyncSSLSocket
    public SSLEngine getSSLEngine() {
        return this.engine;
    }

    void addToPending(ByteBufferList out, ByteBuffer mReadTmp) {
        mReadTmp.flip();
        if (mReadTmp.hasRemaining()) {
            out.add(mReadTmp);
        } else {
            ByteBufferList.reclaim(mReadTmp);
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void end() {
        this.mSocket.end();
    }

    public String getHost() {
        return this.mHost;
    }

    public int getPort() {
        return this.mPort;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleHandshakeStatus(SSLEngineResult.HandshakeStatus status) {
        if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable task = this.engine.getDelegatedTask();
            task.run();
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            write(this.writeList);
        }
        if (status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            this.dataCallback.onDataAvailable(this, new ByteBufferList());
        }
        try {
            try {
                if (this.finishedHandshake) {
                    return;
                }
                if (this.engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING || this.engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                    if (this.clientMode) {
                        TrustManager[] trustManagers = this.trustManagers;
                        if (trustManagers == null) {
                            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                            tmf.init((KeyStore) null);
                            trustManagers = tmf.getTrustManagers();
                        }
                        boolean trusted = false;
                        Exception peerUnverifiedCause = null;
                        int length = trustManagers.length;
                        int i = 0;
                        while (true) {
                            if (i >= length) {
                                break;
                            }
                            TrustManager tm = trustManagers[i];
                            try {
                                X509TrustManager xtm = (X509TrustManager) tm;
                                this.peerCertificates = (X509Certificate[]) this.engine.getSession().getPeerCertificates();
                                xtm.checkServerTrusted(this.peerCertificates, "SSL");
                                if (this.mHost != null) {
                                    if (this.hostnameVerifier == null) {
                                        StrictHostnameVerifier verifier = new StrictHostnameVerifier();
                                        verifier.verify(this.mHost, StrictHostnameVerifier.getCNs(this.peerCertificates[0]), StrictHostnameVerifier.getDNSSubjectAlts(this.peerCertificates[0]));
                                    } else if (!this.hostnameVerifier.verify(this.mHost, this.engine.getSession())) {
                                        throw new SSLException("hostname <" + this.mHost + "> has been denied");
                                    }
                                }
                                trusted = true;
                            } catch (GeneralSecurityException e) {
                                e = e;
                                peerUnverifiedCause = e;
                                i++;
                            } catch (SSLException e2) {
                                e = e2;
                                peerUnverifiedCause = e;
                                i++;
                            }
                            peerUnverifiedCause = e;
                            i++;
                        }
                        this.finishedHandshake = true;
                        if (!trusted) {
                            AsyncSSLException e3 = new AsyncSSLException(peerUnverifiedCause);
                            report(e3);
                            if (!e3.getIgnore()) {
                                throw e3;
                            }
                        }
                    } else {
                        this.finishedHandshake = true;
                    }
                    this.handshakeCallback.onHandshakeCompleted(null, this);
                    this.handshakeCallback = null;
                    this.mSocket.setClosedCallback(null);
                    getServer().post(new Runnable() { // from class: com.koushikdutta.async.AsyncSSLSocketWrapper.6
                        @Override // java.lang.Runnable
                        public void run() {
                            if (AsyncSSLSocketWrapper.this.mWriteableCallback != null) {
                                AsyncSSLSocketWrapper.this.mWriteableCallback.onWriteable();
                            }
                        }
                    });
                    onDataAvailable();
                }
            } catch (GeneralSecurityException ex) {
                report(ex);
            }
        } catch (AsyncSSLException ex2) {
            report(ex2);
        } catch (NoSuchAlgorithmException ex3) {
            throw new RuntimeException(ex3);
        }
    }

    int calculateAlloc(int remaining) {
        int alloc = (remaining * 3) / 2;
        if (alloc == 0) {
            return 8192;
        }
        return alloc;
    }

    @Override // com.koushikdutta.async.DataSink
    public void write(ByteBufferList bb) {
        if (!this.mWrapping && this.mSink.remaining() <= 0) {
            this.mWrapping = true;
            SSLEngineResult res = null;
            ByteBuffer writeBuf = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
            do {
                if (!this.finishedHandshake || bb.remaining() != 0) {
                    int remaining = bb.remaining();
                    try {
                        ByteBuffer[] arr = bb.getAllArray();
                        res = this.engine.wrap(arr, writeBuf);
                        bb.addAll(arr);
                        writeBuf.flip();
                        this.writeList.add(writeBuf);
                        if (!$assertionsDisabled && this.writeList.hasRemaining()) {
                            throw new AssertionError();
                        }
                        if (this.writeList.remaining() > 0) {
                            this.mSink.write(this.writeList);
                        }
                        int previousCapacity = writeBuf.capacity();
                        if (res.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                            writeBuf = ByteBufferList.obtain(previousCapacity * 2);
                            remaining = -1;
                        } else {
                            writeBuf = ByteBufferList.obtain(calculateAlloc(bb.remaining()));
                            handleHandshakeStatus(res.getHandshakeStatus());
                        }
                    } catch (SSLException e) {
                        report(e);
                    }
                    if (remaining == bb.remaining() && (res == null || res.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP)) {
                        break;
                    }
                } else {
                    break;
                }
            } while (this.mSink.remaining() == 0);
            this.mWrapping = false;
            ByteBufferList.reclaim(writeBuf);
        }
    }

    @Override // com.koushikdutta.async.DataSink
    public void setWriteableCallback(WritableCallback handler) {
        this.mWriteableCallback = handler;
    }

    @Override // com.koushikdutta.async.DataSink
    public WritableCallback getWriteableCallback() {
        return this.mWriteableCallback;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void report(Exception e) {
        HandshakeCallback hs = this.handshakeCallback;
        if (hs != null) {
            this.handshakeCallback = null;
            this.mSocket.setDataCallback(new DataCallback.NullDataCallback());
            this.mSocket.end();
            this.mSocket.setClosedCallback(null);
            this.mSocket.close();
            hs.onHandshakeCompleted(e, null);
            return;
        }
        CompletedCallback cb = getEndCallback();
        if (cb != null) {
            cb.onCompleted(e);
        }
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void setDataCallback(DataCallback callback) {
        this.mDataCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public DataCallback getDataCallback() {
        return this.mDataCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isChunked() {
        return this.mSocket.isChunked();
    }

    @Override // com.koushikdutta.async.DataSink
    public boolean isOpen() {
        return this.mSocket.isOpen();
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
        this.mEndCallback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public CompletedCallback getEndCallback() {
        return this.mEndCallback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void pause() {
        this.mSocket.pause();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void resume() {
        this.mSocket.resume();
        onDataAvailable();
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isPaused() {
        return this.mSocket.isPaused();
    }

    @Override // com.koushikdutta.async.AsyncSocket, com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.mSocket.getServer();
    }

    @Override // com.koushikdutta.async.wrapper.AsyncSocketWrapper
    public AsyncSocket getSocket() {
        return this.mSocket;
    }

    @Override // com.koushikdutta.async.wrapper.DataEmitterWrapper
    public DataEmitter getDataEmitter() {
        return this.mSocket;
    }

    @Override // com.koushikdutta.async.AsyncSSLSocket
    public X509Certificate[] getPeerCertificates() {
        return this.peerCertificates;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public String charset() {
        return null;
    }
}
