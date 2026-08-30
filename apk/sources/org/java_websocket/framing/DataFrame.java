package org.java_websocket.framing;

import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;

/* JADX INFO: loaded from: classes.dex */
public abstract class DataFrame extends FramedataImpl1 {
    public DataFrame(Framedata.Opcode opcode) {
        super(opcode);
    }

    @Override // org.java_websocket.framing.FramedataImpl1
    public void isValid() throws InvalidDataException {
    }
}
