package org.java_websocket.framing;

import org.java_websocket.framing.Framedata;

/* JADX INFO: loaded from: classes.dex */
public class PongFrame extends ControlFrame {
    public PongFrame() {
        super(Framedata.Opcode.PONG);
    }

    public PongFrame(PingFrame pingFrame) {
        super(Framedata.Opcode.PONG);
        setPayload(pingFrame.getPayloadData());
    }
}
