package org.java_websocket.framing;

import org.java_websocket.framing.Framedata;

/* JADX INFO: loaded from: classes.dex */
public class BinaryFrame extends DataFrame {
    public BinaryFrame() {
        super(Framedata.Opcode.BINARY);
    }
}
