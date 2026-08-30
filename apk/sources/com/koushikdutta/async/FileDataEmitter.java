package com.koushikdutta.async;

import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.util.StreamUtility;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/* JADX INFO: loaded from: classes.dex */
public class FileDataEmitter extends DataEmitterBase {
    DataCallback callback;
    FileChannel channel;
    File file;
    boolean paused;
    ByteBufferList pending = new ByteBufferList();
    Runnable pumper = new Runnable() { // from class: com.koushikdutta.async.FileDataEmitter.1
        @Override // java.lang.Runnable
        public void run() {
            try {
                if (FileDataEmitter.this.channel == null) {
                    FileDataEmitter.this.channel = new FileInputStream(FileDataEmitter.this.file).getChannel();
                }
                if (!FileDataEmitter.this.pending.isEmpty()) {
                    Util.emitAllData(FileDataEmitter.this, FileDataEmitter.this.pending);
                    if (!FileDataEmitter.this.pending.isEmpty()) {
                        return;
                    }
                }
                do {
                    ByteBuffer b = ByteBufferList.obtain(8192);
                    if (-1 == FileDataEmitter.this.channel.read(b)) {
                        FileDataEmitter.this.report(null);
                        return;
                    }
                    b.flip();
                    FileDataEmitter.this.pending.add(b);
                    Util.emitAllData(FileDataEmitter.this, FileDataEmitter.this.pending);
                    if (FileDataEmitter.this.pending.remaining() != 0) {
                        return;
                    }
                } while (!FileDataEmitter.this.isPaused());
            } catch (Exception e) {
                FileDataEmitter.this.report(e);
            }
        }
    };
    AsyncServer server;

    public FileDataEmitter(AsyncServer server, File file) {
        this.server = server;
        this.file = file;
        this.paused = !server.isAffinityThread();
        if (!this.paused) {
            doResume();
        }
    }

    @Override // com.koushikdutta.async.DataEmitterBase, com.koushikdutta.async.DataEmitter
    public void setDataCallback(DataCallback callback) {
        this.callback = callback;
    }

    @Override // com.koushikdutta.async.DataEmitterBase, com.koushikdutta.async.DataEmitter
    public DataCallback getDataCallback() {
        return this.callback;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isChunked() {
        return false;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void pause() {
        this.paused = true;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void resume() {
        this.paused = false;
        doResume();
    }

    @Override // com.koushikdutta.async.DataEmitterBase
    protected void report(Exception e) {
        StreamUtility.closeQuietly(this.channel);
        super.report(e);
    }

    private void doResume() {
        this.server.post(this.pumper);
    }

    @Override // com.koushikdutta.async.DataEmitter
    public boolean isPaused() {
        return this.paused;
    }

    @Override // com.koushikdutta.async.DataEmitter, com.koushikdutta.async.DataSink
    public AsyncServer getServer() {
        return this.server;
    }

    @Override // com.koushikdutta.async.DataEmitter
    public void close() {
        try {
            this.channel.close();
        } catch (Exception e) {
        }
    }
}
