package org.java_websocket.drafts;

import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;

/* JADX INFO: loaded from: classes.dex */
@Deprecated
public class Draft_17 extends Draft_10 {
    @Override // org.java_websocket.drafts.Draft_10, org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) throws InvalidHandshakeException {
        int v = readVersion(handshakedata);
        return v == 13 ? Draft.HandshakeState.MATCHED : Draft.HandshakeState.NOT_MATCHED;
    }

    @Override // org.java_websocket.drafts.Draft_10, org.java_websocket.drafts.Draft
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request) {
        super.postProcessHandshakeRequestAsClient(request);
        request.put("Sec-WebSocket-Version", "13");
        return request;
    }

    @Override // org.java_websocket.drafts.Draft_10, org.java_websocket.drafts.Draft
    public Draft copyInstance() {
        return new Draft_17();
    }
}
