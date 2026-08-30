package com.bullb.r2d2_nanopisystem.UDP;

import android.content.Context;
import android.util.Log;
import com.koushikdutta.async.AsyncDatagramSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class UDPServer {
    public static final String TAG = "UDPServer";
    private AsyncDatagramSocket asyncDatagramSocket;
    private Context context;
    private final InetSocketAddress host;
    private UDPReceiver udpReceiver;

    public interface UDPReceiver {
        void onReceive(String str);
    }

    public UDPServer(String host, int port) {
        this.host = new InetSocketAddress(host, port);
        setup();
    }

    private void setup() {
        try {
            Log.d(TAG, "Setup");
            this.asyncDatagramSocket = AsyncServer.getDefault().openDatagram(this.host, true);
            this.asyncDatagramSocket.setDataCallback(new DataCallback() { // from class: com.bullb.r2d2_nanopisystem.UDP.UDPServer.1
                @Override // com.koushikdutta.async.callback.DataCallback
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    byte[] data = bb.getAllByteArray();
                    if (UDPServer.this.udpReceiver != null) {
                        UDPServer.this.udpReceiver.onReceive(new String(data));
                    }
                }
            });
            this.asyncDatagramSocket.setClosedCallback(new CompletedCallback() { // from class: com.bullb.r2d2_nanopisystem.UDP.UDPServer.2
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    if (ex != null) {
                        ex.printStackTrace();
                    }
                    Log.d(UDPServer.TAG, "[Client] Successfully closed connection");
                }
            });
            this.asyncDatagramSocket.setEndCallback(new CompletedCallback() { // from class: com.bullb.r2d2_nanopisystem.UDP.UDPServer.3
                @Override // com.koushikdutta.async.callback.CompletedCallback
                public void onCompleted(Exception ex) {
                    ex.printStackTrace();
                    if (ex != null) {
                        ex.printStackTrace();
                    }
                    Log.d(UDPServer.TAG, "[Client] Successfully end connection");
                }
            });
        } catch (IOException e) {
            Log.d(TAG, "Setup Exception");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setUdpReceiver(UDPReceiver udpReceiver) {
        this.udpReceiver = udpReceiver;
    }

    public void cancel() {
        this.udpReceiver = null;
        this.asyncDatagramSocket.close();
    }

    public void send(String msg) {
        Log.d(TAG, "Send:" + msg);
        this.asyncDatagramSocket.send(this.host, ByteBuffer.wrap(msg.getBytes()));
    }
}
