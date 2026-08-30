package com.koushikdutta.async.http.body;

import com.koushikdutta.async.DataSink;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.Headers;
import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.NameValuePair;
import java.io.File;
import java.util.List;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public class Part {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    private long length;
    Multimap mContentDisposition;
    Headers mHeaders;

    static {
        $assertionsDisabled = !Part.class.desiredAssertionStatus();
    }

    public Part(Headers headers) {
        this.length = -1L;
        this.mHeaders = headers;
        this.mContentDisposition = Multimap.parseSemicolonDelimited(this.mHeaders.get(CONTENT_DISPOSITION));
    }

    public String getName() {
        return this.mContentDisposition.getString("name");
    }

    public Part(String name, long length, List<NameValuePair> contentDisposition) {
        this.length = -1L;
        this.length = length;
        this.mHeaders = new Headers();
        StringBuilder builder = new StringBuilder(String.format(Locale.ENGLISH, "form-data; name=\"%s\"", name));
        if (contentDisposition != null) {
            for (NameValuePair pair : contentDisposition) {
                builder.append(String.format(Locale.ENGLISH, "; %s=\"%s\"", pair.getName(), pair.getValue()));
            }
        }
        this.mHeaders.set(CONTENT_DISPOSITION, builder.toString());
        this.mContentDisposition = Multimap.parseSemicolonDelimited(this.mHeaders.get(CONTENT_DISPOSITION));
    }

    public Headers getRawHeaders() {
        return this.mHeaders;
    }

    public String getContentType() {
        return this.mHeaders.get("Content-Type");
    }

    public void setContentType(String contentType) {
        this.mHeaders.set("Content-Type", contentType);
    }

    public String getFilename() {
        String file = this.mContentDisposition.getString("filename");
        if (file == null) {
            return null;
        }
        return new File(file).getName();
    }

    public boolean isFile() {
        return this.mContentDisposition.containsKey("filename");
    }

    public long length() {
        return this.length;
    }

    public void write(DataSink sink, CompletedCallback callback) {
        if (!$assertionsDisabled) {
            throw new AssertionError();
        }
    }
}
