package com.koushikdutta.async;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.callback.WritableCallback;
import com.koushikdutta.async.util.Allocator;
import com.koushikdutta.async.util.StreamUtility;
import com.koushikdutta.async.wrapper.AsyncSocketWrapper;
import com.koushikdutta.async.wrapper.DataEmitterWrapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class Util {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static boolean SUPRESS_DEBUG_EXCEPTIONS;

    static {
        $assertionsDisabled = !Util.class.desiredAssertionStatus();
        SUPRESS_DEBUG_EXCEPTIONS = false;
    }

    public static void emitAllData(DataEmitter emitter, ByteBufferList list) {
        int remaining;
        DataCallback handler = null;
        while (!emitter.isPaused() && (handler = emitter.getDataCallback()) != null && (remaining = list.remaining()) > 0) {
            handler.onDataAvailable(emitter, list);
            if (remaining == list.remaining() && handler == emitter.getDataCallback() && !emitter.isPaused()) {
                System.out.println("handler: " + handler);
                list.recycle();
                if (!SUPRESS_DEBUG_EXCEPTIONS) {
                    if (!$assertionsDisabled) {
                        throw new AssertionError();
                    }
                    throw new RuntimeException("mDataHandler failed to consume data, yet remains the mDataHandler.");
                }
                return;
            }
        }
        if (list.remaining() != 0 && !emitter.isPaused()) {
            System.out.println("handler: " + handler);
            System.out.println("emitter: " + emitter);
            list.recycle();
            if (!SUPRESS_DEBUG_EXCEPTIONS) {
                if (!$assertionsDisabled) {
                    throw new AssertionError();
                }
                throw new RuntimeException("Not all data was consumed by Util.emitAllData");
            }
        }
    }

    public static void pump(InputStream is, DataSink ds, CompletedCallback callback) {
        pump(is, 2147483647L, ds, callback);
    }

    public static void pump(final InputStream is, final long max, final DataSink ds, final CompletedCallback callback) {
        final CompletedCallback wrapper = new CompletedCallback() { // from class: com.koushikdutta.async.Util.1
            boolean reported;

            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (!this.reported) {
                    this.reported = true;
                    callback.onCompleted(ex);
                }
            }
        };
        WritableCallback cb = new WritableCallback() { // from class: com.koushikdutta.async.Util.2
            int totalRead = 0;
            ByteBufferList pending = new ByteBufferList();
            Allocator allocator = new Allocator();

            private void cleanup() {
                ds.setClosedCallback(null);
                ds.setWriteableCallback(null);
                this.pending.recycle();
                StreamUtility.closeQuietly(is);
            }

            @Override // com.koushikdutta.async.callback.WritableCallback
            public void onWriteable() {
                do {
                    try {
                        if (!this.pending.hasRemaining()) {
                            ByteBuffer b = this.allocator.allocate();
                            long toRead = Math.min(max - ((long) this.totalRead), b.capacity());
                            int read = is.read(b.array(), 0, (int) toRead);
                            if (read == -1 || this.totalRead == max) {
                                cleanup();
                                wrapper.onCompleted(null);
                                return;
                            } else {
                                this.allocator.track(read);
                                this.totalRead += read;
                                b.position(0);
                                b.limit(read);
                                this.pending.add(b);
                            }
                        }
                        ds.write(this.pending);
                    } catch (Exception e) {
                        cleanup();
                        wrapper.onCompleted(e);
                        return;
                    }
                } while (!this.pending.hasRemaining());
            }
        };
        ds.setWriteableCallback(cb);
        ds.setClosedCallback(wrapper);
        cb.onWriteable();
    }

    public static void pump(final DataEmitter emitter, final DataSink sink, final CompletedCallback callback) {
        DataCallback dataCallback = new DataCallback() { // from class: com.koushikdutta.async.Util.3
            @Override // com.koushikdutta.async.callback.DataCallback
            public void onDataAvailable(DataEmitter emitter2, ByteBufferList bb) {
                sink.write(bb);
                if (bb.remaining() > 0) {
                    emitter2.pause();
                }
            }
        };
        emitter.setDataCallback(dataCallback);
        sink.setWriteableCallback(new WritableCallback() { // from class: com.koushikdutta.async.Util.4
            @Override // com.koushikdutta.async.callback.WritableCallback
            public void onWriteable() {
                emitter.resume();
            }
        });
        final CompletedCallback wrapper = new CompletedCallback() { // from class: com.koushikdutta.async.Util.5
            boolean reported;

            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (!this.reported) {
                    this.reported = true;
                    emitter.setDataCallback(null);
                    emitter.setEndCallback(null);
                    sink.setClosedCallback(null);
                    sink.setWriteableCallback(null);
                    callback.onCompleted(ex);
                }
            }
        };
        emitter.setEndCallback(wrapper);
        sink.setClosedCallback(new CompletedCallback() { // from class: com.koushikdutta.async.Util.6
            @Override // com.koushikdutta.async.callback.CompletedCallback
            public void onCompleted(Exception ex) {
                if (ex == null) {
                    ex = new IOException("sink was closed before emitter ended");
                }
                wrapper.onCompleted(ex);
            }
        });
    }

    public static void stream(AsyncSocket s1, AsyncSocket s2, CompletedCallback callback) {
        pump(s1, s2, callback);
        pump(s2, s1, callback);
    }

    public static void pump(File file, DataSink ds, final CompletedCallback callback) {
        try {
            if (file == null || ds == null) {
                callback.onCompleted(null);
            } else {
                final InputStream is = new FileInputStream(file);
                pump(is, ds, new CompletedCallback() { // from class: com.koushikdutta.async.Util.7
                    @Override // com.koushikdutta.async.callback.CompletedCallback
                    public void onCompleted(Exception ex) {
                        try {
                            is.close();
                            callback.onCompleted(ex);
                        } catch (IOException e) {
                            callback.onCompleted(e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            callback.onCompleted(e);
        }
    }

    public static void writeAll(final DataSink sink, final ByteBufferList bb, final CompletedCallback callback) {
        WritableCallback wc = new WritableCallback() { // from class: com.koushikdutta.async.Util.8
            @Override // com.koushikdutta.async.callback.WritableCallback
            public void onWriteable() {
                sink.write(bb);
                if (bb.remaining() == 0 && callback != null) {
                    sink.setWriteableCallback(null);
                    callback.onCompleted(null);
                }
            }
        };
        sink.setWriteableCallback(wc);
        wc.onWriteable();
    }

    public static void writeAll(DataSink sink, byte[] bytes, CompletedCallback callback) {
        ByteBuffer bb = ByteBufferList.obtain(bytes.length);
        bb.put(bytes);
        bb.flip();
        ByteBufferList bbl = new ByteBufferList();
        bbl.add(bb);
        writeAll(sink, bbl, callback);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r1v0, types: [T extends com.koushikdutta.async.AsyncSocket, com.koushikdutta.async.AsyncSocket, java.lang.Object] */
    /* JADX WARN: Type inference failed for: r1v1 */
    /* JADX WARN: Type inference failed for: r1v4, types: [T extends com.koushikdutta.async.AsyncSocket, com.koushikdutta.async.AsyncSocket, java.lang.Object] */
    public static <T extends AsyncSocket> T getWrappedSocket(AsyncSocket asyncSocket, Class<T> cls) {
        if (!cls.isInstance(asyncSocket)) {
            while (asyncSocket instanceof AsyncSocketWrapper) {
                asyncSocket = (T) ((AsyncSocketWrapper) asyncSocket).getSocket();
                if (cls.isInstance(asyncSocket)) {
                    return asyncSocket;
                }
            }
            return null;
        }
        return asyncSocket;
    }

    public static DataEmitter getWrappedDataEmitter(DataEmitter emitter, Class wrappedClass) {
        if (!wrappedClass.isInstance(emitter)) {
            while (emitter instanceof DataEmitterWrapper) {
                emitter = ((AsyncSocketWrapper) emitter).getSocket();
                if (wrappedClass.isInstance(emitter)) {
                    return emitter;
                }
            }
            return null;
        }
        return emitter;
    }

    public static void end(DataEmitter emitter, Exception e) {
        if (emitter != null) {
            end(emitter.getEndCallback(), e);
        }
    }

    public static void end(CompletedCallback end, Exception e) {
        if (end != null) {
            end.onCompleted(e);
        }
    }

    public static void writable(DataSink emitter) {
        if (emitter != null) {
            writable(emitter.getWriteableCallback());
        }
    }

    public static void writable(WritableCallback writable) {
        if (writable != null) {
            writable.onWriteable();
        }
    }
}
