package com.koushikdutta.async.http.body;

import com.koushikdutta.async.http.BasicNameValuePair;
import com.koushikdutta.async.http.NameValuePair;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class FilePart extends StreamPart {
    File file;

    public FilePart(String name, final File file) {
        super(name, (int) file.length(), new ArrayList<NameValuePair>() { // from class: com.koushikdutta.async.http.body.FilePart.1
            {
                add(new BasicNameValuePair("filename", file.getName()));
            }
        });
        this.file = file;
    }

    @Override // com.koushikdutta.async.http.body.StreamPart
    protected InputStream getInputStream() throws IOException {
        return new FileInputStream(this.file);
    }
}
