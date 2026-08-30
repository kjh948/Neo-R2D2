package com.koushikdutta.async.stream;

import com.koushikdutta.async.AsyncServer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/* JADX INFO: loaded from: classes.dex */
public class FileDataSink extends OutputStreamDataSink {
    File file;

    public FileDataSink(AsyncServer server, File file) {
        super(server);
        this.file = file;
    }

    @Override // com.koushikdutta.async.stream.OutputStreamDataSink
    public OutputStream getOutputStream() throws IOException {
        OutputStream ret = super.getOutputStream();
        if (ret == null) {
            OutputStream ret2 = new FileOutputStream(this.file);
            setOutputStream(ret2);
            return ret2;
        }
        return ret;
    }
}
