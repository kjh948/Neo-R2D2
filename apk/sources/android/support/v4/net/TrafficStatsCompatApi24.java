package android.support.v4.net;

import android.annotation.TargetApi;
import android.net.TrafficStats;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import java.net.DatagramSocket;
import java.net.SocketException;

/* JADX INFO: loaded from: classes.dex */
@RequiresApi(24)
@TargetApi(24)
@RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
public class TrafficStatsCompatApi24 {
    public static void tagDatagramSocket(DatagramSocket socket) throws SocketException {
        TrafficStats.tagDatagramSocket(socket);
    }

    public static void untagDatagramSocket(DatagramSocket socket) throws SocketException {
        TrafficStats.untagDatagramSocket(socket);
    }
}
