package org.java_websocket.framing;

import org.java_websocket.framing.Framedata;

/* JADX INFO: loaded from: classes.dex */
public class PingFrame extends ControlFrame {
    public PingFrame() {
        super(Framedata.Opcode.PING);
    }
}
