package com.koushikdutta.async.http;

import android.os.Build;
import com.koushikdutta.async.http.AsyncHttpClientMiddleware;
import java.lang.reflect.Field;
import java.util.Hashtable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/* JADX INFO: loaded from: classes.dex */
public class SSLEngineSNIConfigurator implements AsyncSSLEngineConfigurator {
    Hashtable<String, EngineHolder> holders = new Hashtable<>();

    private static class EngineHolder implements AsyncSSLEngineConfigurator {
        Field peerHost;
        Field peerPort;
        boolean skipReflection;
        Field sslParameters;
        Field useSni;

        @Override // com.koushikdutta.async.http.AsyncSSLEngineConfigurator
        public SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort) {
            return null;
        }

        public EngineHolder(Class engineClass) {
            try {
                this.peerHost = engineClass.getSuperclass().getDeclaredField("peerHost");
                this.peerHost.setAccessible(true);
                this.peerPort = engineClass.getSuperclass().getDeclaredField("peerPort");
                this.peerPort.setAccessible(true);
                this.sslParameters = engineClass.getDeclaredField("sslParameters");
                this.sslParameters.setAccessible(true);
                this.useSni = this.sslParameters.getType().getDeclaredField("useSni");
                this.useSni.setAccessible(true);
            } catch (NoSuchFieldException e) {
            }
        }

        @Override // com.koushikdutta.async.http.AsyncSSLEngineConfigurator
        public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
            if (this.useSni != null && !this.skipReflection) {
                try {
                    this.peerHost.set(engine, host);
                    this.peerPort.set(engine, Integer.valueOf(port));
                    Object sslp = this.sslParameters.get(engine);
                    this.useSni.set(sslp, true);
                } catch (IllegalAccessException e) {
                }
            }
        }
    }

    @Override // com.koushikdutta.async.http.AsyncSSLEngineConfigurator
    public SSLEngine createEngine(SSLContext sslContext, String peerHost, int peerPort) {
        boolean skipReflection = "GmsCore_OpenSSL".equals(sslContext.getProvider().getName()) || Build.VERSION.SDK_INT >= 23;
        if (skipReflection) {
            SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
            return engine;
        }
        SSLEngine engine2 = sslContext.createSSLEngine();
        return engine2;
    }

    EngineHolder ensureHolder(SSLEngine engine) {
        String name = engine.getClass().getCanonicalName();
        EngineHolder holder = this.holders.get(name);
        if (holder == null) {
            EngineHolder holder2 = new EngineHolder(engine.getClass());
            this.holders.put(name, holder2);
            return holder2;
        }
        return holder;
    }

    @Override // com.koushikdutta.async.http.AsyncSSLEngineConfigurator
    public void configureEngine(SSLEngine engine, AsyncHttpClientMiddleware.GetSocketData data, String host, int port) {
        EngineHolder holder = ensureHolder(engine);
        holder.configureEngine(engine, data, host, port);
    }
}
