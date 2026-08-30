package org.java_websocket.drafts;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidFrameException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.exceptions.NotSendableException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.TextFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.util.Charsetfunctions;

/* JADX INFO: loaded from: classes.dex */
@Deprecated
public class Draft_75 extends Draft {
    public static final byte CR = 13;
    public static final byte END_OF_FRAME = -1;
    public static final byte LF = 10;
    public static final byte START_OF_FRAME = 0;
    protected ByteBuffer currentFrame;
    protected boolean readingState = false;
    protected List<Framedata> readyframes = new LinkedList();
    private final Random reuseableRandom = new Random();

    @Override // org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsClient(ClientHandshake request, ServerHandshake response) {
        return (request.getFieldValue("WebSocket-Origin").equals(response.getFieldValue("Origin")) && basicAccept(response)) ? Draft.HandshakeState.MATCHED : Draft.HandshakeState.NOT_MATCHED;
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft.HandshakeState acceptHandshakeAsServer(ClientHandshake handshakedata) {
        return (handshakedata.hasFieldValue("Origin") && basicAccept(handshakedata)) ? Draft.HandshakeState.MATCHED : Draft.HandshakeState.NOT_MATCHED;
    }

    @Override // org.java_websocket.drafts.Draft
    public ByteBuffer createBinaryFrame(Framedata framedata) {
        if (framedata.getOpcode() != Framedata.Opcode.TEXT) {
            throw new RuntimeException("only text frames supported");
        }
        ByteBuffer pay = framedata.getPayloadData();
        ByteBuffer b = ByteBuffer.allocate(pay.remaining() + 2);
        b.put((byte) 0);
        pay.mark();
        b.put(pay);
        pay.reset();
        b.put((byte) -1);
        b.flip();
        return b;
    }

    @Override // org.java_websocket.drafts.Draft
    public List<Framedata> createFrames(ByteBuffer binary, boolean mask) {
        throw new RuntimeException("not yet implemented");
    }

    @Override // org.java_websocket.drafts.Draft
    public List<Framedata> createFrames(String text, boolean mask) {
        TextFrame frame = new TextFrame();
        frame.setPayload(ByteBuffer.wrap(Charsetfunctions.utf8Bytes(text)));
        frame.setTransferemasked(mask);
        try {
            frame.isValid();
            return Collections.singletonList(frame);
        } catch (InvalidDataException e) {
            throw new NotSendableException(e);
        }
    }

    @Override // org.java_websocket.drafts.Draft
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(ClientHandshakeBuilder request) throws InvalidHandshakeException {
        request.put("Upgrade", "WebSocket");
        request.put("Connection", "Upgrade");
        if (!request.hasFieldValue("Origin")) {
            request.put("Origin", "random" + this.reuseableRandom.nextInt());
        }
        return request;
    }

    @Override // org.java_websocket.drafts.Draft
    public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
        response.setHttpStatusMessage("Web Socket Protocol Handshake");
        response.put("Upgrade", "WebSocket");
        response.put("Connection", request.getFieldValue("Connection"));
        response.put("WebSocket-Origin", request.getFieldValue("Origin"));
        String location = "ws://" + request.getFieldValue("Host") + request.getResourceDescriptor();
        response.put("WebSocket-Location", location);
        return response;
    }

    protected List<Framedata> translateRegularFrame(ByteBuffer buffer) throws InvalidDataException {
        while (buffer.hasRemaining()) {
            byte newestByte = buffer.get();
            if (newestByte == 0) {
                if (this.readingState) {
                    throw new InvalidFrameException("unexpected START_OF_FRAME");
                }
                this.readingState = true;
            } else if (newestByte == -1) {
                if (!this.readingState) {
                    throw new InvalidFrameException("unexpected END_OF_FRAME");
                }
                if (this.currentFrame != null) {
                    this.currentFrame.flip();
                    TextFrame curframe = new TextFrame();
                    curframe.setPayload(this.currentFrame);
                    this.readyframes.add(curframe);
                    this.currentFrame = null;
                    buffer.mark();
                }
                this.readingState = false;
            } else {
                if (!this.readingState) {
                    return null;
                }
                if (this.currentFrame == null) {
                    this.currentFrame = createBuffer();
                } else if (!this.currentFrame.hasRemaining()) {
                    this.currentFrame = increaseBuffer(this.currentFrame);
                }
                this.currentFrame.put(newestByte);
            }
        }
        List<Framedata> frames = this.readyframes;
        this.readyframes = new LinkedList();
        return frames;
    }

    @Override // org.java_websocket.drafts.Draft
    public List<Framedata> translateFrame(ByteBuffer buffer) throws InvalidDataException {
        List<Framedata> frames = translateRegularFrame(buffer);
        if (frames == null) {
            throw new InvalidDataException(1002);
        }
        return frames;
    }

    @Override // org.java_websocket.drafts.Draft
    public void reset() {
        this.readingState = false;
        this.currentFrame = null;
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft.CloseHandshakeType getCloseHandshakeType() {
        return Draft.CloseHandshakeType.NONE;
    }

    public ByteBuffer createBuffer() {
        return ByteBuffer.allocate(INITIAL_FAMESIZE);
    }

    public ByteBuffer increaseBuffer(ByteBuffer full) throws InvalidDataException {
        full.flip();
        ByteBuffer newbuffer = ByteBuffer.allocate(checkAlloc(full.capacity() * 2));
        newbuffer.put(full);
        return newbuffer;
    }

    @Override // org.java_websocket.drafts.Draft
    public Draft copyInstance() {
        return new Draft_75();
    }
}
