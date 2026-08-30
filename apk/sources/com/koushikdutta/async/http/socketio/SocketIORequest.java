package com.koushikdutta.async.http.socketio;

import android.net.Uri;
import com.koushikdutta.async.http.AsyncHttpPost;

/* JADX INFO: loaded from: classes.dex */
public class SocketIORequest extends AsyncHttpPost {
    Config config;
    String endpoint;
    String query;

    public SocketIORequest(String uri) {
        this(uri, "");
    }

    public Config getConfig() {
        return this.config;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public String getQuery() {
        return this.query;
    }

    public SocketIORequest(String uri, String endpoint) {
        this(uri, endpoint, null);
    }

    public SocketIORequest(String uri, String endpoint, String query) {
        this(uri, endpoint, query, null);
    }

    public SocketIORequest(String uri, String endpoint, String query, Config config) {
        super(Uri.parse(uri + (query == null ? "" : "?" + query)).buildUpon().encodedPath("/socket.io/1/").build().toString());
        this.config = config == null ? new Config() : config;
        this.endpoint = endpoint;
        this.query = query;
    }

    public static class Config {
        boolean randomizeReconnectDelay = false;
        long reconnectDelay = 1000;
        long reconnectDelayMax = 0;

        public void setRandomizeReconnectDelay(boolean randomizeReconnectDelay) {
            this.randomizeReconnectDelay = randomizeReconnectDelay;
        }

        public boolean isRandomizeReconnectDelay() {
            return this.randomizeReconnectDelay;
        }

        public void setReconnectDelay(long reconnectDelay) {
            if (reconnectDelay < 0) {
                throw new IllegalArgumentException("reconnectDelay must be >= 0");
            }
            this.reconnectDelay = reconnectDelay;
        }

        public long getReconnectDelay() {
            return this.reconnectDelay;
        }

        public void setReconnectDelayMax(long reconnectDelayMax) {
            if (this.reconnectDelay < 0) {
                throw new IllegalArgumentException("reconnectDelayMax must be >= 0");
            }
            this.reconnectDelayMax = reconnectDelayMax;
        }

        public long getReconnectDelayMax() {
            return this.reconnectDelayMax;
        }
    }
}
