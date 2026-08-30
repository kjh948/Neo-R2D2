package com.koushikdutta.async.http;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.body.AsyncHttpRequestBody;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.body.MultipartFormDataBody;
import com.koushikdutta.async.http.body.StringBody;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.filter.ChunkedInputFilter;
import com.koushikdutta.async.http.filter.ContentLengthFilter;
import com.koushikdutta.async.http.filter.GZIPInputFilter;
import com.koushikdutta.async.http.filter.InflaterInputFilter;

/* JADX INFO: loaded from: classes.dex */
public class HttpUtil {
    public static AsyncHttpRequestBody getBody(DataEmitter emitter, CompletedCallback reporter, Headers headers) {
        String contentType = headers.get("Content-Type");
        if (contentType != null) {
            String[] values = contentType.split(";");
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }
            for (String ct : values) {
                if (UrlEncodedFormBody.CONTENT_TYPE.equals(ct)) {
                    return new UrlEncodedFormBody();
                }
                if ("application/json".equals(ct)) {
                    return new JSONObjectBody();
                }
                if (StringBody.CONTENT_TYPE.equals(ct)) {
                    return new StringBody();
                }
                if (MultipartFormDataBody.CONTENT_TYPE.equals(ct)) {
                    return new MultipartFormDataBody(values);
                }
            }
        }
        return null;
    }

    static class EndEmitter extends FilteredDataEmitter {
        private EndEmitter() {
        }

        public static EndEmitter create(AsyncServer server, final Exception e) {
            EndEmitter ret = new EndEmitter();
            server.post(new Runnable() { // from class: com.koushikdutta.async.http.HttpUtil.EndEmitter.1
                @Override // java.lang.Runnable
                public void run() {
                    EndEmitter.this.report(e);
                }
            });
            return ret;
        }
    }

    public static DataEmitter getBodyDecoder(DataEmitter emitter, Protocol protocol, Headers headers, boolean server) {
        long _contentLength;
        try {
            _contentLength = Long.parseLong(headers.get("Content-Length"));
        } catch (Exception e) {
            _contentLength = -1;
        }
        long contentLength = _contentLength;
        if (-1 != contentLength) {
            if (contentLength < 0) {
                EndEmitter ender = EndEmitter.create(emitter.getServer(), new BodyDecoderException("not using chunked encoding, and no content-length found."));
                ender.setDataEmitter(emitter);
                return ender;
            }
            if (contentLength == 0) {
                EndEmitter ender2 = EndEmitter.create(emitter.getServer(), null);
                ender2.setDataEmitter(emitter);
                return ender2;
            }
            ContentLengthFilter contentLengthWatcher = new ContentLengthFilter(contentLength);
            contentLengthWatcher.setDataEmitter(emitter);
            emitter = contentLengthWatcher;
        } else if ("chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"))) {
            ChunkedInputFilter chunker = new ChunkedInputFilter();
            chunker.setDataEmitter(emitter);
            emitter = chunker;
        } else if ((server || protocol == Protocol.HTTP_1_1) && !"close".equalsIgnoreCase(headers.get("Connection"))) {
            EndEmitter ender3 = EndEmitter.create(emitter.getServer(), null);
            ender3.setDataEmitter(emitter);
            return ender3;
        }
        if ("gzip".equals(headers.get("Content-Encoding"))) {
            GZIPInputFilter gunzipper = new GZIPInputFilter();
            gunzipper.setDataEmitter(emitter);
            return gunzipper;
        }
        if ("deflate".equals(headers.get("Content-Encoding"))) {
            InflaterInputFilter inflater = new InflaterInputFilter();
            inflater.setDataEmitter(emitter);
            return inflater;
        }
        return emitter;
    }

    public static boolean isKeepAlive(Protocol protocol, Headers headers) {
        String connection = headers.get("Connection");
        if (connection == null) {
            return protocol == Protocol.HTTP_1_1;
        }
        return "keep-alive".equalsIgnoreCase(connection);
    }

    public static boolean isKeepAlive(String protocol, Headers headers) {
        String connection = headers.get("Connection");
        if (connection == null) {
            return Protocol.get(protocol) == Protocol.HTTP_1_1;
        }
        return "keep-alive".equalsIgnoreCase(connection);
    }

    public static int contentLength(Headers headers) {
        String cl = headers.get("Content-Length");
        if (cl == null) {
            return -1;
        }
        try {
            return Integer.parseInt(cl);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
