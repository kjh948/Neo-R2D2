package com.koushikdutta.async.dns;

import com.koushikdutta.async.AsyncDatagramSocket;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/* JADX INFO: loaded from: classes.dex */
public class Dns {
    public static Future<DnsResponse> lookup(String host) {
        return lookup(AsyncServer.getDefault(), host, false, null);
    }

    private static int setFlag(int flags, int value, int offset) {
        return (value << offset) | flags;
    }

    private static int setQuery(int flags) {
        return setFlag(flags, 0, 0);
    }

    private static int setRecursion(int flags) {
        return setFlag(flags, 1, 8);
    }

    private static void addName(ByteBuffer bb, String name) {
        String[] parts = name.split("\\.");
        for (String part : parts) {
            bb.put((byte) part.length());
            bb.put(part.getBytes());
        }
        bb.put((byte) 0);
    }

    public static Future<DnsResponse> lookup(AsyncServer server, String host) {
        return lookup(server, host, false, null);
    }

    public static Cancellable multicastLookup(AsyncServer server, String host, FutureCallback<DnsResponse> callback) {
        return lookup(server, host, true, callback);
    }

    public static Cancellable multicastLookup(String host, FutureCallback<DnsResponse> callback) {
        return multicastLookup(AsyncServer.getDefault(), host, callback);
    }

    /* JADX WARN: Generic types in debug info not equals: java.lang.Object != com.koushikdutta.async.future.SimpleFuture<com.koushikdutta.async.dns.DnsResponse> */
    public static Future<DnsResponse> lookup(AsyncServer server, String host, final boolean multicast, final FutureCallback<DnsResponse> callback) {
        final AsyncDatagramSocket dgram;
        ByteBuffer packet = ByteBufferList.obtain(1024).order(ByteOrder.BIG_ENDIAN);
        short id = (short) new Random().nextInt();
        short flags = (short) setQuery(0);
        if (!multicast) {
            flags = (short) setRecursion(flags);
        }
        packet.putShort(id);
        packet.putShort(flags);
        packet.putShort(multicast ? (short) 1 : (short) 2);
        packet.putShort((short) 0);
        packet.putShort((short) 0);
        packet.putShort((short) 0);
        addName(packet, host);
        packet.putShort(multicast ? (short) 12 : (short) 1);
        packet.putShort((short) 1);
        if (!multicast) {
            addName(packet, host);
            packet.putShort((short) 28);
            packet.putShort((short) 1);
        }
        packet.flip();
        try {
            if (!multicast) {
                dgram = server.connectDatagram(new InetSocketAddress("8.8.8.8", 53));
            } else {
                dgram = AsyncServer.getDefault().openDatagram(new InetSocketAddress(0), true);
                Field field = DatagramSocket.class.getDeclaredField("impl");
                field.setAccessible(true);
                Object impl = field.get(dgram.getSocket());
                Method method = impl.getClass().getDeclaredMethod("join", InetAddress.class);
                method.setAccessible(true);
                method.invoke(impl, InetAddress.getByName("224.0.0.251"));
                ((DatagramSocket) dgram.getSocket()).setBroadcast(true);
            }
            final SimpleFuture<DnsResponse> ret = new SimpleFuture<DnsResponse>() { // from class: com.koushikdutta.async.dns.Dns.1
                @Override // com.koushikdutta.async.future.SimpleCancellable
                protected void cleanup() {
                    super.cleanup();
                    dgram.close();
                }
            };
            dgram.setDataCallback(new DataCallback() { // from class: com.koushikdutta.async.dns.Dns.2
                @Override // com.koushikdutta.async.callback.DataCallback
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    try {
                        DnsResponse response = DnsResponse.parse(bb);
                        response.source = dgram.getRemoteAddress();
                        if (!multicast) {
                            dgram.close();
                            ret.setComplete(response);
                        } else {
                            callback.onCompleted(null, response);
                        }
                    } catch (Exception e) {
                    }
                    bb.recycle();
                }
            });
            if (!multicast) {
                dgram.write(new ByteBufferList(packet));
            } else {
                dgram.send(new InetSocketAddress("224.0.0.251", 5353), packet);
            }
            return ret;
        } catch (Exception e) {
            SimpleFuture<DnsResponse> ret2 = new SimpleFuture<>();
            ret2.setComplete(e);
            if (multicast) {
                callback.onCompleted(e, null);
            }
            return ret2;
        }
    }
}
