package org.java_websocket.drafts;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.IncompleteHandshakeException;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidFrameException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;

/* JADX INFO: loaded from: classes.dex */
@Deprecated
public class Draft_76 extends Draft_75 {
    private static final byte[] closehandshake = {-1, 0};
    private boolean failed = false;
    private final Random reuseableRandom = new Random();

    public static byte[] createChallenge(String key1, String key2, byte[] key3) throws InvalidHandshakeException {
        byte[] part1 = getPart(key1);
        byte[] part2 = getPart(key2);
        byte[] challenge = {part1[0], part1[1], part1[2], part1[3], part2[0], part2[1], part2[2], part2[3], key3[0], key3[1], key3[2], key3[3], key3[4], key3[5], key3[6], key3[7]};
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return md5.digest(challenge);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateKey() {
        Random r = new Random();
        long spaces = r.nextInt(12) + 1;
        int max = new Long(4294967295L / spaces).intValue();
        int number = r.nextInt(Math.abs(max)) + 1;
        long product = ((long) number) * spaces;
        String key = Long.toString(product);
        int numChars = r.nextInt(12) + 1;
        for (int i = 0; i < numChars; i++) {
            int position = r.nextInt(key.length());
            int position2 = Math.abs(position);
            char randChar = (char) (r.nextInt(95) + 33);
            if (randChar >= '0' && randChar <= '9') {
                randChar = (char) (randChar - 15);
            }
            key = new StringBuilder(key).insert(position2, randChar).toString();
        }
        for (int i2 = 0; i2 < spaces; i2++) {
            int position3 = r.nextInt(key.length() - 1) + 1;
            key = new StringBuilder(key).insert(Math.abs(position3), " ").toString();
        }
        return key;
    }

    private static byte[] getPart(String key) throws InvalidHandshakeException {
        try {
            long keyNumber = Long.parseLong(key.replaceAll("[^0-9]", ""));
            long keySpace = key.split(" ").length - 1;
            if (keySpace == 0) {
                throw new InvalidHandshakeException("invalid Sec-WebSocket-Key (/key2/)");
            }
            long part = keyNumber / keySpace;
            return new byte[]{(byte) (part >> 24), (byte) ((part << 8) >> 24), (byte) ((part << 16) >> 24), (byte) ((part << 24) >> 24)};
        } catch (NumberFormatException e) {
            throw new InvalidHandshakeException("invalid Sec-WebSocket-Key (/key1/ or /key2/)");
        }
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response) {
        if (this.failed) {
            return Draft.HandshakeState.NOT_MATCHED;
        }
        try {
            if (!response.getFieldValue("Sec-WebSocket-Origin").equals(request.getFieldValue("Origin")) || !basicAccept(response)) {
                return Draft.HandshakeState.NOT_MATCHED;
            }
            byte[] content = response.getContent();
            if (content == null || content.length == 0) {
                throw new IncompleteHandshakeException();
            }
            if (Arrays.equals(content, createChallenge(request.getFieldValue("Sec-WebSocket-Key1"), request.getFieldValue("Sec-WebSocket-Key2"), request.getContent()))) {
                return Draft.HandshakeState.MATCHED;
            }
            return Draft.HandshakeState.NOT_MATCHED;
        } catch (InvalidHandshakeException e) {
            throw new RuntimeException("bad handshakerequest", e);
        }
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) {
        return (handshakedata.getFieldValue("Upgrade").equals("WebSocket") && handshakedata.getFieldValue("Connection").contains("Upgrade") && handshakedata.getFieldValue("Sec-WebSocket-Key1").length() > 0 && handshakedata.getFieldValue("Sec-WebSocket-Key2").length() > 0 && handshakedata.hasFieldValue("Origin")) ? Draft.HandshakeState.MATCHED : Draft.HandshakeState.NOT_MATCHED;
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request) {
        request.put("Upgrade", "WebSocket");
        request.put("Connection", "Upgrade");
        request.put("Sec-WebSocket-Key1", generateKey());
        request.put("Sec-WebSocket-Key2", generateKey());
        if (!request.hasFieldValue("Origin")) {
            request.put("Origin", "random" + this.reuseableRandom.nextInt());
        }
        byte[] key3 = new byte[8];
        this.reuseableRandom.nextBytes(key3);
        request.setContent(key3);
        return request;
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
        response.setHttpStatusMessage("WebSocket Protocol Handshake");
        response.put("Upgrade", "WebSocket");
        response.put("Connection", request.getFieldValue("Connection"));
        response.put("Sec-WebSocket-Origin", request.getFieldValue("Origin"));
        String location = "ws://" + request.getFieldValue("Host") + request.getResourceDescriptor();
        response.put("Sec-WebSocket-Location", location);
        String key1 = request.getFieldValue("Sec-WebSocket-Key1");
        String key2 = request.getFieldValue("Sec-WebSocket-Key2");
        byte[] key3 = request.getContent();
        if (key1 == null || key2 == null || key3 == null || key3.length != 8) {
            throw new InvalidHandshakeException("Bad keys");
        }
        response.setContent(createChallenge(key1, key2, key3));
        return response;
    }

    @Override // org.java_websocket.drafts.Draft
    public Handshakedata translateHandshake(ByteBuffer buf) throws InvalidHandshakeException {
        HandshakeBuilder bui = translateHandshakeHttp(buf, this.role);
        if ((bui.hasFieldValue("Sec-WebSocket-Key1") || this.role == WebSocket.Role.CLIENT) && !bui.hasFieldValue("Sec-WebSocket-Version")) {
            byte[] key3 = new byte[this.role == WebSocket.Role.SERVER ? 8 : 16];
            try {
                buf.get(key3);
                bui.setContent(key3);
            } catch (BufferUnderflowException e) {
                throw new IncompleteHandshakeException(buf.capacity() + 16);
            }
        }
        return bui;
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public List<Framedata> translateFrame(ByteBuffer buffer) throws InvalidDataException {
        buffer.mark();
        List<Framedata> frames = super.translateRegularFrame(buffer);
        if (frames != null) {
            return frames;
        }
        buffer.reset();
        List<Framedata> frames2 = this.readyframes;
        this.readingState = true;
        if (this.currentFrame == null) {
            this.currentFrame = ByteBuffer.allocate(2);
            if (buffer.remaining() > this.currentFrame.remaining()) {
                throw new InvalidFrameException();
            }
            this.currentFrame.put(buffer);
            if (!this.currentFrame.hasRemaining()) {
                if (Arrays.equals(this.currentFrame.array(), closehandshake)) {
                    CloseFrame closeFrame = new CloseFrame();
                    closeFrame.setCode(1000);
                    closeFrame.isValid();
                    frames2.add(closeFrame);
                    return frames2;
                }
                throw new InvalidFrameException();
            }
            this.readyframes = new LinkedList();
            return frames2;
        }
        throw new InvalidFrameException();
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public ByteBuffer createBinaryFrame(Framedata framedata) {
        return framedata.getOpcode() == Framedata.Opcode.CLOSING ? ByteBuffer.wrap(closehandshake) : super.createBinaryFrame(framedata);
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public Draft.CloseHandshakeType getCloseHandshakeType() {
        return Draft.CloseHandshakeType.ONEWAY;
    }

    @Override // org.java_websocket.drafts.Draft_75, org.java_websocket.drafts.Draft
    public Draft copyInstance() {
        return new Draft_76();
    }
}
