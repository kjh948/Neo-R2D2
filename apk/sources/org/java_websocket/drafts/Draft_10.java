package org.java_websocket.drafts;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidFrameException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.exceptions.LimitExedeedException;
import org.java_websocket.exceptions.NotSendableException;
import org.java_websocket.framing.BinaryFrame;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.FramedataImpl1;
import org.java_websocket.framing.TextFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.util.Base64;
import org.java_websocket.util.Charsetfunctions;

/* JADX INFO: loaded from: classes.dex */
@Deprecated
public class Draft_10 extends Draft {
    static final /* synthetic */ boolean $assertionsDisabled;
    private ByteBuffer incompleteframe;
    private final Random reuseableRandom = new Random();

    static {
        $assertionsDisabled = !Draft_10.class.desiredAssertionStatus();
    }

    private class IncompleteException extends Throwable {
        private static final long serialVersionUID = 7330519489840500997L;
        private int preferedsize;

        public IncompleteException(int preferedsize) {
            this.preferedsize = preferedsize;
        }

        public int getPreferedSize() {
            return this.preferedsize;
        }
    }

    public static int readVersion(Handshakedata handshakedata) {
        String vers = handshakedata.getFieldValue("Sec-WebSocket-Version");
        if (vers.length() <= 0) {
            return -1;
        }
        try {
            return new Integer(vers.trim()).intValue();
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response) throws InvalidHandshakeException {
        if (!request.hasFieldValue("Sec-WebSocket-Key") || !response.hasFieldValue("Sec-WebSocket-Accept")) {
            return Draft.HandshakeState.NOT_MATCHED;
        }
        String seckey_answere = response.getFieldValue("Sec-WebSocket-Accept");
        String seckey_challenge = request.getFieldValue("Sec-WebSocket-Key");
        if (generateFinalKey(seckey_challenge).equals(seckey_answere)) {
            return Draft.HandshakeState.MATCHED;
        }
        return Draft.HandshakeState.NOT_MATCHED;
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) throws InvalidHandshakeException {
        int v = readVersion(handshakedata);
        if (v == 7 || v == 8) {
            return basicAccept(handshakedata) ? Draft.HandshakeState.MATCHED : Draft.HandshakeState.NOT_MATCHED;
        }
        return Draft.HandshakeState.NOT_MATCHED;
    }

    @Override // org.java_websocket.drafts.Draft
    public ByteBuffer createBinaryFrame(Framedata framedata) {
        int sizebytes;
        ByteBuffer mes = framedata.getPayloadData();
        boolean mask = this.role == WebSocket.Role.CLIENT;
        if (mes.remaining() <= 125) {
            sizebytes = 1;
        } else {
            sizebytes = mes.remaining() <= 65535 ? 2 : 8;
        }
        ByteBuffer buf = ByteBuffer.allocate((mask ? 4 : 0) + (sizebytes > 1 ? sizebytes + 1 : sizebytes) + 1 + mes.remaining());
        byte optcode = fromOpcode(framedata.getOpcode());
        byte one = (byte) (framedata.isFin() ? -128 : 0);
        buf.put((byte) (one | optcode));
        byte[] payloadlengthbytes = toByteArray(mes.remaining(), sizebytes);
        if (!$assertionsDisabled && payloadlengthbytes.length != sizebytes) {
            throw new AssertionError();
        }
        if (sizebytes == 1) {
            buf.put((byte) ((mask ? (byte) -128 : (byte) 0) | payloadlengthbytes[0]));
        } else if (sizebytes == 2) {
            buf.put((byte) ((mask ? -128 : 0) | 126));
            buf.put(payloadlengthbytes);
        } else if (sizebytes == 8) {
            buf.put((byte) ((mask ? -128 : 0) | 127));
            buf.put(payloadlengthbytes);
        } else {
            throw new RuntimeException("Size representation not supported/specified");
        }
        if (mask) {
            ByteBuffer maskkey = ByteBuffer.allocate(4);
            maskkey.putInt(this.reuseableRandom.nextInt());
            buf.put(maskkey.array());
            int i = 0;
            while (mes.hasRemaining()) {
                buf.put((byte) (mes.get() ^ maskkey.get(i % 4)));
                i++;
            }
        } else {
            buf.put(mes);
        }
        if (!$assertionsDisabled && buf.remaining() != 0) {
            throw new AssertionError(buf.remaining());
        }
        buf.flip();
        return buf;
    }

    @Override // org.java_websocket.drafts.Draft
    public List<Framedata> createFrames(ByteBuffer binary, boolean mask) {
        BinaryFrame curframe = new BinaryFrame();
        curframe.setPayload(binary);
        curframe.setTransferemasked(mask);
        try {
            curframe.isValid();
            return Collections.singletonList(curframe);
        } catch (InvalidDataException e) {
            throw new NotSendableException(e);
        }
    }

    @Override // org.java_websocket.drafts.Draft
    public List<Framedata> createFrames(String text, boolean mask) {
        TextFrame curframe = new TextFrame();
        curframe.setPayload(ByteBuffer.wrap(Charsetfunctions.utf8Bytes(text)));
        curframe.setTransferemasked(mask);
        try {
            curframe.isValid();
            return Collections.singletonList(curframe);
        } catch (InvalidDataException e) {
            throw new NotSendableException(e);
        }
    }

    private byte fromOpcode(Framedata.Opcode opcode) {
        if (opcode == Framedata.Opcode.CONTINUOUS) {
            return (byte) 0;
        }
        if (opcode == Framedata.Opcode.TEXT) {
            return (byte) 1;
        }
        if (opcode == Framedata.Opcode.BINARY) {
            return (byte) 2;
        }
        if (opcode == Framedata.Opcode.CLOSING) {
            return (byte) 8;
        }
        if (opcode == Framedata.Opcode.PING) {
            return (byte) 9;
        }
        if (opcode == Framedata.Opcode.PONG) {
            return (byte) 10;
        }
        throw new RuntimeException("Don't know how to handle " + opcode.toString());
    }

    private String generateFinalKey(String in) {
        String seckey = in.trim();
        String acc = seckey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        try {
            MessageDigest sh1 = MessageDigest.getInstance("SHA1");
            return Base64.encodeBytes(sh1.digest(acc.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override // org.java_websocket.drafts.Draft
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request) {
        request.put("Upgrade", "websocket");
        request.put("Connection", "Upgrade");
        request.put("Sec-WebSocket-Version", "8");
        byte[] random = new byte[16];
        this.reuseableRandom.nextBytes(random);
        request.put("Sec-WebSocket-Key", Base64.encodeBytes(random));
        return request;
    }

    @Override // org.java_websocket.drafts.Draft
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
        response.put("Upgrade", "websocket");
        response.put("Connection", request.getFieldValue("Connection"));
        response.setHttpStatusMessage("Switching Protocols");
        String seckey = request.getFieldValue("Sec-WebSocket-Key");
        if (seckey == null) {
            throw new InvalidHandshakeException("missing Sec-WebSocket-Key");
        }
        response.put("Sec-WebSocket-Accept", generateFinalKey(seckey));
        return response;
    }

    private byte[] toByteArray(long val, int bytecount) {
        byte[] buffer = new byte[bytecount];
        int highest = (bytecount * 8) - 8;
        for (int i = 0; i < bytecount; i++) {
            buffer[i] = (byte) (val >>> (highest - (i * 8)));
        }
        return buffer;
    }

    private Framedata.Opcode toOpcode(byte opcode) throws InvalidFrameException {
        switch (opcode) {
            case 0:
                return Framedata.Opcode.CONTINUOUS;
            case 1:
                return Framedata.Opcode.TEXT;
            case 2:
                return Framedata.Opcode.BINARY;
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            default:
                throw new InvalidFrameException("Unknown opcode " + ((int) opcode));
            case 8:
                return Framedata.Opcode.CLOSING;
            case 9:
                return Framedata.Opcode.PING;
            case 10:
                return Framedata.Opcode.PONG;
        }
    }

    @Override // org.java_websocket.drafts.Draft
    public List<Framedata> translateFrame(ByteBuffer buffer) throws InvalidDataException {
        List<Framedata> frames;
        while (true) {
            frames = new LinkedList<>();
            if (this.incompleteframe == null) {
                break;
            }
            try {
                buffer.mark();
                int available_next_byte_count = buffer.remaining();
                int expected_next_byte_count = this.incompleteframe.remaining();
                if (expected_next_byte_count > available_next_byte_count) {
                    this.incompleteframe.put(buffer.array(), buffer.position(), available_next_byte_count);
                    buffer.position(buffer.position() + available_next_byte_count);
                    return Collections.emptyList();
                }
                this.incompleteframe.put(buffer.array(), buffer.position(), expected_next_byte_count);
                buffer.position(buffer.position() + expected_next_byte_count);
                Framedata cur = translateSingleFrame((ByteBuffer) this.incompleteframe.duplicate().position(0));
                frames.add(cur);
                this.incompleteframe = null;
            } catch (IncompleteException e) {
                this.incompleteframe.limit();
                ByteBuffer extendedframe = ByteBuffer.allocate(checkAlloc(e.getPreferedSize()));
                if (!$assertionsDisabled && extendedframe.limit() <= this.incompleteframe.limit()) {
                    throw new AssertionError();
                }
                this.incompleteframe.rewind();
                extendedframe.put(this.incompleteframe);
                this.incompleteframe = extendedframe;
            }
        }
        while (buffer.hasRemaining()) {
            buffer.mark();
            try {
                Framedata cur2 = translateSingleFrame(buffer);
                frames.add(cur2);
            } catch (IncompleteException e2) {
                buffer.reset();
                int pref = e2.getPreferedSize();
                this.incompleteframe = ByteBuffer.allocate(checkAlloc(pref));
                this.incompleteframe.put(buffer);
                return frames;
            }
        }
        return frames;
    }

    public Framedata translateSingleFrame(ByteBuffer buffer) throws InvalidDataException, IncompleteException {
        FramedataImpl1 frame;
        int maxpacketsize = buffer.remaining();
        int realpacketsize = 2;
        if (maxpacketsize < 2) {
            throw new IncompleteException(2);
        }
        byte b1 = buffer.get();
        boolean FIN = (b1 >> 8) != 0;
        boolean rsv1 = false;
        if ((b1 & 64) != 0) {
            rsv1 = true;
        }
        boolean rsv2 = (b1 & 32) != 0;
        boolean rsv3 = (b1 & 16) != 0;
        if (rsv1 || rsv2 || rsv3) {
            throw new InvalidFrameException("bad rsv rsv1: " + rsv1 + " rsv2: " + rsv2 + " rsv3: " + rsv3);
        }
        byte b2 = buffer.get();
        boolean MASK = (b2 & (-128)) != 0;
        int payloadlength = (byte) (b2 & 127);
        Framedata.Opcode optcode = toOpcode((byte) (b1 & 15));
        if (!FIN && (optcode == Framedata.Opcode.PING || optcode == Framedata.Opcode.PONG || optcode == Framedata.Opcode.CLOSING)) {
            throw new InvalidFrameException("control frames may no be fragmented");
        }
        if (payloadlength < 0 || payloadlength > 125) {
            if (optcode == Framedata.Opcode.PING || optcode == Framedata.Opcode.PONG || optcode == Framedata.Opcode.CLOSING) {
                throw new InvalidFrameException("more than 125 octets");
            }
            if (payloadlength == 126) {
                realpacketsize = 2 + 2;
                if (maxpacketsize < realpacketsize) {
                    throw new IncompleteException(realpacketsize);
                }
                byte[] sizebytes = {0, buffer.get(), buffer.get()};
                payloadlength = new BigInteger(sizebytes).intValue();
            } else {
                realpacketsize = 2 + 8;
                if (maxpacketsize < realpacketsize) {
                    throw new IncompleteException(realpacketsize);
                }
                byte[] bytes = new byte[8];
                for (int i = 0; i < 8; i++) {
                    bytes[i] = buffer.get();
                }
                long length = new BigInteger(bytes).longValue();
                if (length > 2147483647L) {
                    throw new LimitExedeedException("Payloadsize is to big...");
                }
                payloadlength = (int) length;
            }
        }
        int realpacketsize2 = realpacketsize + (MASK ? 4 : 0) + payloadlength;
        if (maxpacketsize < realpacketsize2) {
            throw new IncompleteException(realpacketsize2);
        }
        ByteBuffer payload = ByteBuffer.allocate(checkAlloc(payloadlength));
        if (MASK) {
            byte[] maskskey = new byte[4];
            buffer.get(maskskey);
            for (int i2 = 0; i2 < payloadlength; i2++) {
                payload.put((byte) (buffer.get() ^ maskskey[i2 % 4]));
            }
        } else {
            payload.put(buffer.array(), buffer.position(), payload.limit());
            buffer.position(buffer.position() + payload.limit());
        }
        if (optcode == Framedata.Opcode.CLOSING) {
            frame = new CloseFrame();
        } else {
            frame = FramedataImpl1.get(optcode);
            frame.setFin(FIN);
        }
        payload.flip();
        frame.setPayload(payload);
        frame.isValid();
        return frame;
    }

    @Override // org.java_websocket.drafts.Draft
    public void reset() {
        this.incompleteframe = null;
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft copyInstance() {
        return new Draft_10();
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft.CloseHandshakeType getCloseHandshakeType() {
        return Draft.CloseHandshakeType.TWOWAY;
    }
}
