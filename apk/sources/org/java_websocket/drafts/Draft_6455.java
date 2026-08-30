package org.java_websocket.drafts;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.ServerHandshakeBuilder;

/* JADX INFO: loaded from: classes.dex */
public class Draft_6455 extends Draft_17 {
    @Override // org.java_websocket.drafts.Draft_10, org.java_websocket.drafts.Draft
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
        super.postProcessHandshakeResponseAsServer(request, response);
        response.setHttpStatusMessage("Web Socket Protocol Handshake");
        response.put("Server", "TooTallNate Java-WebSocket");
        response.put("Date", getServerTime());
        return response;
    }

    @Override // org.java_websocket.drafts.Draft_17, org.java_websocket.drafts.Draft_10, org.java_websocket.drafts.Draft
    public Draft copyInstance() {
        return new Draft_6455();
    }

    private String getServerTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(calendar.getTime());
    }
}
