package org.java_websocket.framing;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.util.ByteBufferUtils;
import org.java_websocket.util.Charsetfunctions;

/* JADX INFO: loaded from: classes.dex */
public abstract class FramedataImpl1 implements Framedata {
    private Framedata.Opcode optcode;
    private ByteBuffer unmaskedpayload = ByteBufferUtils.getEmptyByteBuffer();
    private boolean fin = true;
    private boolean transferemasked = false;
    private boolean rsv1 = false;
    private boolean rsv2 = false;
    private boolean rsv3 = false;

    public abstract void isValid() throws InvalidDataException;

    public FramedataImpl1(Framedata.Opcode op) {
        this.optcode = op;
    }

    @Override // org.java_websocket.framing.Framedata
    public boolean isRSV1() {
        return this.rsv1;
    }

    @Override // org.java_websocket.framing.Framedata
    public boolean isRSV2() {
        return this.rsv2;
    }

    @Override // org.java_websocket.framing.Framedata
    public boolean isRSV3() {
        return this.rsv3;
    }

    @Override // org.java_websocket.framing.Framedata
    public boolean isFin() {
        return this.fin;
    }

    @Override // org.java_websocket.framing.Framedata
    public Framedata.Opcode getOpcode() {
        return this.optcode;
    }

    @Override // org.java_websocket.framing.Framedata
    public boolean getTransfereMasked() {
        return this.transferemasked;
    }

    @Override // org.java_websocket.framing.Framedata
    public ByteBuffer getPayloadData() {
        return this.unmaskedpayload;
    }

    @Override // org.java_websocket.framing.Framedata
    public void append(Framedata nextframe) {
        ByteBuffer b = nextframe.getPayloadData();
        if (this.unmaskedpayload == null) {
            this.unmaskedpayload = ByteBuffer.allocate(b.remaining());
            b.mark();
            this.unmaskedpayload.put(b);
            b.reset();
        } else {
            b.mark();
            this.unmaskedpayload.position(this.unmaskedpayload.limit());
            this.unmaskedpayload.limit(this.unmaskedpayload.capacity());
            if (b.remaining() > this.unmaskedpayload.remaining()) {
                ByteBuffer tmp = ByteBuffer.allocate(b.remaining() + this.unmaskedpayload.capacity());
                this.unmaskedpayload.flip();
                tmp.put(this.unmaskedpayload);
                tmp.put(b);
                this.unmaskedpayload = tmp;
            } else {
                this.unmaskedpayload.put(b);
            }
            this.unmaskedpayload.rewind();
            b.reset();
        }
        this.fin = nextframe.isFin();
    }

    public String toString() {
        return "Framedata{ optcode:" + getOpcode() + ", fin:" + isFin() + ", rsv1:" + isRSV1() + ", rsv2:" + isRSV2() + ", rsv3:" + isRSV3() + ", payloadlength:[pos:" + this.unmaskedpayload.position() + ", len:" + this.unmaskedpayload.remaining() + "], payload:" + Arrays.toString(Charsetfunctions.utf8Bytes(new String(this.unmaskedpayload.array()))) + "}";
    }

    public void setPayload(ByteBuffer payload) {
        this.unmaskedpayload = payload;
    }

    public void setFin(boolean fin) {
        this.fin = fin;
    }

    public void setRSV1(boolean rsv1) {
        this.rsv1 = rsv1;
    }

    public void setRSV2(boolean rsv2) {
        this.rsv2 = rsv2;
    }

    public void setRSV3(boolean rsv3) {
        this.rsv3 = rsv3;
    }

    public void setTransferemasked(boolean transferemasked) {
        this.transferemasked = transferemasked;
    }

    public static FramedataImpl1 get(Framedata.Opcode opcode) {
        if (opcode == null) {
            throw new IllegalArgumentException("Supplied opcode cannot be null");
        }
        switch (opcode) {
            case PING:
                return new PingFrame();
            case PONG:
                return new PongFrame();
            case TEXT:
                return new TextFrame();
            case BINARY:
                return new BinaryFrame();
            case CLOSING:
                return new CloseFrame();
            case CONTINUOUS:
                return new ContinuousFrame();
            default:
                throw new IllegalArgumentException("Supplied opcode is invalid");
        }
    }
}
