package org.java_websocket.framing;

import org.java_websocket.framing.Framedata;

/* JADX INFO: loaded from: classes.dex */
public class TextFrame extends DataFrame {
    public TextFrame() {
        super(Framedata.Opcode.TEXT);
    }
}
