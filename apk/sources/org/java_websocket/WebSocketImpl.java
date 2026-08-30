package org.java_websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.IncompleteHandshakeException;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.PingFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.util.Charsetfunctions;

/* JADX INFO: loaded from: classes.dex */
public class WebSocketImpl implements WebSocket {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static boolean DEBUG;
    public static int RCVBUF;
    public ByteChannel channel;
    private Integer closecode;
    private Boolean closedremotely;
    private String closemessage;
    private Framedata current_continuous_frame;
    private Draft draft;
    private volatile boolean flushandclosestate;
    private ClientHandshake handshakerequest;
    public final BlockingQueue<ByteBuffer> inQueue;
    public SelectionKey key;
    private List<Draft> knownDrafts;
    private long lastPong;
    public final BlockingQueue<ByteBuffer> outQueue;
    private WebSocket.READYSTATE readystate;
    private String resourceDescriptor;
    private WebSocket.Role role;
    private ByteBuffer tmpHandshakeBytes;
    public volatile WebSocketServer.WebSocketWorker workerThread;
    private final WebSocketListener wsl;

    static {
        $assertionsDisabled = !WebSocketImpl.class.desiredAssertionStatus();
        RCVBUF = 16384;
        DEBUG = false;
    }

    public WebSocketImpl(WebSocketListener listener, List<Draft> drafts) {
        this(listener, (Draft) null);
        this.role = WebSocket.Role.SERVER;
        if (drafts == null || drafts.isEmpty()) {
            this.knownDrafts = new ArrayList();
            this.knownDrafts.add(new Draft_6455());
        } else {
            this.knownDrafts = drafts;
        }
    }

    public WebSocketImpl(WebSocketListener listener, Draft draft) {
        this.flushandclosestate = false;
        this.readystate = WebSocket.READYSTATE.NOT_YET_CONNECTED;
        this.draft = null;
        this.current_continuous_frame = null;
        this.tmpHandshakeBytes = ByteBuffer.allocate(0);
        this.handshakerequest = null;
        this.closemessage = null;
        this.closecode = null;
        this.closedremotely = null;
        this.resourceDescriptor = null;
        this.lastPong = System.currentTimeMillis();
        if (listener == null || (draft == null && this.role == WebSocket.Role.SERVER)) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        this.outQueue = new LinkedBlockingQueue();
        this.inQueue = new LinkedBlockingQueue();
        this.wsl = listener;
        this.role = WebSocket.Role.CLIENT;
        if (draft != null) {
            this.draft = draft.copyInstance();
        }
    }

    @Deprecated
    public WebSocketImpl(WebSocketListener listener, Draft draft, Socket socket) {
        this(listener, draft);
    }

    @Deprecated
    public WebSocketImpl(WebSocketListener listener, List<Draft> drafts, Socket socket) {
        this(listener, drafts);
    }

    public void decode(ByteBuffer socketBuffer) {
        if (!$assertionsDisabled && !socketBuffer.hasRemaining()) {
            throw new AssertionError();
        }
        if (DEBUG) {
            System.out.println("process(" + socketBuffer.remaining() + "): {" + (socketBuffer.remaining() > 1000 ? "too big to display" : new String(socketBuffer.array(), socketBuffer.position(), socketBuffer.remaining())) + "}");
        }
        if (this.readystate != WebSocket.READYSTATE.NOT_YET_CONNECTED) {
            if (this.readystate == WebSocket.READYSTATE.OPEN) {
                decodeFrames(socketBuffer);
            }
        } else if (decodeHandshake(socketBuffer)) {
            if (!$assertionsDisabled && this.tmpHandshakeBytes.hasRemaining() == socketBuffer.hasRemaining() && socketBuffer.hasRemaining()) {
                throw new AssertionError();
            }
            if (socketBuffer.hasRemaining()) {
                decodeFrames(socketBuffer);
            } else if (this.tmpHandshakeBytes.hasRemaining()) {
                decodeFrames(this.tmpHandshakeBytes);
            }
        }
        if (!$assertionsDisabled && !isClosing() && !isFlushAndClose() && socketBuffer.hasRemaining()) {
            throw new AssertionError();
        }
    }

    /* JADX WARN: Can't wrap try/catch for region: R(11:0|2|(1:4)(3:13|(1:15)|16)|5|90|6|(6:8|(3:88|10|11)|21|22|(3:24|(1:26)(2:79|(2:83|84))|27)(1:85)|28)|94|29|(2:31|(7:33|(7:36|92|37|(2:100|39)(2:40|(5:101|42|95|43|44)(2:103|102))|91|12|34)|99|50|(1:52)|53|107)(2:54|(2:56|108)(2:57|(2:59|109)(2:60|110))))(3:61|(2:63|(2:65|111)(2:66|(4:97|68|69|112)(1:74)))|28)|(1:(0))) */
    /* JADX WARN: Code restructure failed: missing block: B:76:0x01e7, code lost:
    
        r3 = move-exception;
     */
    /* JADX WARN: Code restructure failed: missing block: B:77:0x01e8, code lost:
    
        close(r3);
     */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:24:0x007b  */
    /* JADX WARN: Removed duplicated region for block: B:85:0x0201  */
    /* JADX WARN: Type inference failed for: r11v51, types: [java.util.Iterator] */
    /* JADX WARN: Type inference failed for: r11v52, types: [java.util.Iterator] */
    /* JADX WARN: Type inference failed for: r11v56 */
    /* JADX WARN: Type inference failed for: r11v57 */
    /* JADX WARN: Type inference failed for: r11v58 */
    /* JADX WARN: Type inference failed for: r11v59, types: [boolean] */
    /* JADX WARN: Type inference failed for: r11v60 */
    /* JADX WARN: Type inference failed for: r11v61 */
    /* JADX WARN: Type inference failed for: r11v62 */
    /* JADX WARN: Type inference failed for: r15v0, types: [org.java_websocket.WebSocket, org.java_websocket.WebSocketImpl] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    private boolean decodeHandshake(java.nio.ByteBuffer r16) {
        /*
            Method dump skipped, instruction units count: 540
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: org.java_websocket.WebSocketImpl.decodeHandshake(java.nio.ByteBuffer):boolean");
    }

    private void decodeFrames(ByteBuffer socketBuffer) {
        try {
            List<Framedata> frames = this.draft.translateFrame(socketBuffer);
            for (Framedata f : frames) {
                if (DEBUG) {
                    System.out.println("matched frame: " + f);
                }
                Framedata.Opcode curop = f.getOpcode();
                boolean fin = f.isFin();
                if (this.readystate != WebSocket.READYSTATE.CLOSING) {
                    if (curop == Framedata.Opcode.CLOSING) {
                        int code = 1005;
                        String reason = "";
                        if (f instanceof CloseFrame) {
                            CloseFrame cf = (CloseFrame) f;
                            code = cf.getCloseCode();
                            reason = cf.getMessage();
                        }
                        if (this.readystate == WebSocket.READYSTATE.CLOSING) {
                            closeConnection(code, reason, true);
                        } else if (this.draft.getCloseHandshakeType() == Draft.CloseHandshakeType.TWOWAY) {
                            close(code, reason, true);
                        } else {
                            flushAndClose(code, reason, false);
                        }
                    } else if (curop == Framedata.Opcode.PING) {
                        this.wsl.onWebsocketPing(this, f);
                    } else if (curop == Framedata.Opcode.PONG) {
                        this.lastPong = System.currentTimeMillis();
                        this.wsl.onWebsocketPong(this, f);
                    } else if (!fin || curop == Framedata.Opcode.CONTINUOUS) {
                        if (curop != Framedata.Opcode.CONTINUOUS) {
                            if (this.current_continuous_frame != null) {
                                throw new InvalidDataException(1002, "Previous continuous frame sequence not completed.");
                            }
                            this.current_continuous_frame = f;
                        } else if (fin) {
                            if (this.current_continuous_frame == null) {
                                throw new InvalidDataException(1002, "Continuous frame sequence was not started.");
                            }
                            if (this.current_continuous_frame.getOpcode() == Framedata.Opcode.TEXT) {
                                int off = Math.max(this.current_continuous_frame.getPayloadData().limit() - 64, 0);
                                this.current_continuous_frame.append(f);
                                if (!Charsetfunctions.isValidUTF8(this.current_continuous_frame.getPayloadData(), off)) {
                                    throw new InvalidDataException(1007);
                                }
                            }
                            this.current_continuous_frame = null;
                        } else if (this.current_continuous_frame == null) {
                            throw new InvalidDataException(1002, "Continuous frame sequence was not started.");
                        }
                        if (curop == Framedata.Opcode.TEXT && !Charsetfunctions.isValidUTF8(f.getPayloadData())) {
                            throw new InvalidDataException(1007);
                        }
                        if (curop == Framedata.Opcode.CONTINUOUS && this.current_continuous_frame != null && this.current_continuous_frame.getOpcode() == Framedata.Opcode.TEXT) {
                            int off2 = Math.max(this.current_continuous_frame.getPayloadData().limit() - 64, 0);
                            this.current_continuous_frame.append(f);
                            if (!Charsetfunctions.isValidUTF8(this.current_continuous_frame.getPayloadData(), off2)) {
                                throw new InvalidDataException(1007);
                            }
                        }
                        try {
                            this.wsl.onWebsocketMessageFragment(this, f);
                        } catch (RuntimeException e) {
                            this.wsl.onWebsocketError(this, e);
                        }
                    } else {
                        if (this.current_continuous_frame != null) {
                            throw new InvalidDataException(1002, "Continuous frame sequence not completed.");
                        }
                        if (curop == Framedata.Opcode.TEXT) {
                            try {
                                this.wsl.onWebsocketMessage(this, Charsetfunctions.stringUtf8(f.getPayloadData()));
                            } catch (RuntimeException e2) {
                                this.wsl.onWebsocketError(this, e2);
                            }
                        } else if (curop == Framedata.Opcode.BINARY) {
                            try {
                                this.wsl.onWebsocketMessage(this, f.getPayloadData());
                            } catch (RuntimeException e3) {
                                this.wsl.onWebsocketError(this, e3);
                            }
                        } else {
                            throw new InvalidDataException(1002, "non control or continious frame expected");
                        }
                    }
                } else {
                    return;
                }
            }
        } catch (InvalidDataException e1) {
            this.wsl.onWebsocketError(this, e1);
            close(e1);
        }
    }

    private void close(int code, String message, boolean remote) {
        if (this.readystate != WebSocket.READYSTATE.CLOSING && this.readystate != WebSocket.READYSTATE.CLOSED) {
            if (this.readystate == WebSocket.READYSTATE.OPEN) {
                if (code == 1006) {
                    if (!$assertionsDisabled && remote) {
                        throw new AssertionError();
                    }
                    this.readystate = WebSocket.READYSTATE.CLOSING;
                    flushAndClose(code, message, false);
                    return;
                }
                if (this.draft.getCloseHandshakeType() != Draft.CloseHandshakeType.NONE) {
                    try {
                        if (!remote) {
                            try {
                                this.wsl.onWebsocketCloseInitiated(this, code, message);
                            } catch (RuntimeException e) {
                                this.wsl.onWebsocketError(this, e);
                            }
                        }
                        CloseFrame closeFrame = new CloseFrame();
                        closeFrame.setReason(message);
                        closeFrame.setCode(code);
                        try {
                            closeFrame.isValid();
                            sendFrame(closeFrame);
                        } catch (InvalidDataException e2) {
                            throw e2;
                        }
                    } catch (InvalidDataException e3) {
                        this.wsl.onWebsocketError(this, e3);
                        flushAndClose(1006, "generated frame is invalid", false);
                    }
                }
                flushAndClose(code, message, remote);
            } else if (code == -3) {
                if (!$assertionsDisabled && !remote) {
                    throw new AssertionError();
                }
                flushAndClose(-3, message, true);
            } else {
                flushAndClose(-1, message, false);
            }
            if (code == 1002) {
                flushAndClose(code, message, remote);
            }
            this.readystate = WebSocket.READYSTATE.CLOSING;
            this.tmpHandshakeBytes = null;
        }
    }

    @Override // org.java_websocket.WebSocket
    public void close(int code, String message) {
        close(code, message, false);
    }

    protected synchronized void closeConnection(int code, String message, boolean remote) {
        if (this.readystate != WebSocket.READYSTATE.CLOSED) {
            if (this.key != null) {
                this.key.cancel();
            }
            if (this.channel != null) {
                try {
                    this.channel.close();
                } catch (IOException e) {
                    this.wsl.onWebsocketError(this, e);
                }
            }
            try {
                this.wsl.onWebsocketClose(this, code, message, remote);
            } catch (RuntimeException e2) {
                this.wsl.onWebsocketError(this, e2);
            }
            if (this.draft != null) {
                this.draft.reset();
            }
            this.handshakerequest = null;
            this.readystate = WebSocket.READYSTATE.CLOSED;
            this.outQueue.clear();
        }
    }

    protected void closeConnection(int code, boolean remote) {
        closeConnection(code, "", remote);
    }

    public void closeConnection() {
        if (this.closedremotely == null) {
            throw new IllegalStateException("this method must be used in conjuction with flushAndClose");
        }
        closeConnection(this.closecode.intValue(), this.closemessage, this.closedremotely.booleanValue());
    }

    @Override // org.java_websocket.WebSocket
    public void closeConnection(int code, String message) {
        closeConnection(code, message, false);
    }

    protected synchronized void flushAndClose(int code, String message, boolean remote) {
        if (!this.flushandclosestate) {
            this.closecode = Integer.valueOf(code);
            this.closemessage = message;
            this.closedremotely = Boolean.valueOf(remote);
            this.flushandclosestate = true;
            this.wsl.onWriteDemand(this);
            try {
                this.wsl.onWebsocketClosing(this, code, message, remote);
            } catch (RuntimeException e) {
                this.wsl.onWebsocketError(this, e);
            }
            if (this.draft != null) {
                this.draft.reset();
            }
            this.handshakerequest = null;
        }
    }

    public void eot() {
        if (getReadyState() == WebSocket.READYSTATE.NOT_YET_CONNECTED) {
            closeConnection(-1, true);
            return;
        }
        if (this.flushandclosestate) {
            closeConnection(this.closecode.intValue(), this.closemessage, this.closedremotely.booleanValue());
            return;
        }
        if (this.draft.getCloseHandshakeType() == Draft.CloseHandshakeType.NONE) {
            closeConnection(1000, true);
            return;
        }
        if (this.draft.getCloseHandshakeType() == Draft.CloseHandshakeType.ONEWAY) {
            if (this.role == WebSocket.Role.SERVER) {
                closeConnection(1006, true);
                return;
            } else {
                closeConnection(1000, true);
                return;
            }
        }
        closeConnection(1006, true);
    }

    @Override // org.java_websocket.WebSocket
    public void close(int code) {
        close(code, "", false);
    }

    public void close(InvalidDataException e) {
        close(e.getCloseCode(), e.getMessage(), false);
    }

    @Override // org.java_websocket.WebSocket
    public void send(String text) throws WebsocketNotConnectedException {
        if (text == null) {
            throw new IllegalArgumentException("Cannot send 'null' data to a WebSocketImpl.");
        }
        send(this.draft.createFrames(text, this.role == WebSocket.Role.CLIENT));
    }

    @Override // org.java_websocket.WebSocket
    public void send(ByteBuffer bytes) throws IllegalArgumentException, WebsocketNotConnectedException {
        if (bytes == null) {
            throw new IllegalArgumentException("Cannot send 'null' data to a WebSocketImpl.");
        }
        send(this.draft.createFrames(bytes, this.role == WebSocket.Role.CLIENT));
    }

    @Override // org.java_websocket.WebSocket
    public void send(byte[] bytes) throws IllegalArgumentException, WebsocketNotConnectedException {
        send(ByteBuffer.wrap(bytes));
    }

    private void send(Collection<Framedata> frames) {
        if (!isOpen()) {
            throw new WebsocketNotConnectedException();
        }
        for (Framedata f : frames) {
            sendFrame(f);
        }
    }

    @Override // org.java_websocket.WebSocket
    public void sendFragmentedFrame(Framedata.Opcode op, ByteBuffer buffer, boolean fin) {
        send(this.draft.continuousFrame(op, buffer, fin));
    }

    @Override // org.java_websocket.WebSocket
    public void sendFrame(Framedata framedata) {
        if (DEBUG) {
            System.out.println("send frame: " + framedata);
        }
        write(this.draft.createBinaryFrame(framedata));
    }

    @Override // org.java_websocket.WebSocket
    public void sendPing() throws NotYetConnectedException {
        sendFrame(new PingFrame());
    }

    @Override // org.java_websocket.WebSocket
    public boolean hasBufferedData() {
        return !this.outQueue.isEmpty();
    }

    private Draft.HandshakeState isFlashEdgeCase(ByteBuffer request) throws IncompleteHandshakeException {
        request.mark();
        if (request.limit() > Draft.FLASH_POLICY_REQUEST.length) {
            return Draft.HandshakeState.NOT_MATCHED;
        }
        if (request.limit() < Draft.FLASH_POLICY_REQUEST.length) {
            throw new IncompleteHandshakeException(Draft.FLASH_POLICY_REQUEST.length);
        }
        int flash_policy_index = 0;
        while (request.hasRemaining()) {
            if (Draft.FLASH_POLICY_REQUEST[flash_policy_index] == request.get()) {
                flash_policy_index++;
            } else {
                request.reset();
                return Draft.HandshakeState.NOT_MATCHED;
            }
        }
        return Draft.HandshakeState.MATCHED;
    }

    public void startHandshake(ClientHandshakeBuilder handshakedata) throws InvalidHandshakeException {
        if (!$assertionsDisabled && this.readystate == WebSocket.READYSTATE.CONNECTING) {
            throw new AssertionError("shall only be called once");
        }
        this.handshakerequest = this.draft.postProcessHandshakeRequestAsClient(handshakedata);
        this.resourceDescriptor = handshakedata.getResourceDescriptor();
        if (!$assertionsDisabled && this.resourceDescriptor == null) {
            throw new AssertionError();
        }
        try {
            this.wsl.onWebsocketHandshakeSentAsClient(this, this.handshakerequest);
            write(this.draft.createHandshake(this.handshakerequest, this.role));
        } catch (RuntimeException e) {
            this.wsl.onWebsocketError(this, e);
            throw new InvalidHandshakeException("rejected because of" + e);
        } catch (InvalidDataException e2) {
            throw new InvalidHandshakeException("Handshake data rejected by client.");
        }
    }

    private void write(ByteBuffer buf) {
        if (DEBUG) {
            System.out.println("write(" + buf.remaining() + "): {" + (buf.remaining() > 1000 ? "too big to display" : new String(buf.array())) + "}");
        }
        this.outQueue.add(buf);
        this.wsl.onWriteDemand(this);
    }

    private void write(List<ByteBuffer> bufs) {
        for (ByteBuffer b : bufs) {
            write(b);
        }
    }

    private void open(Handshakedata d) {
        if (DEBUG) {
            System.out.println("open using draft: " + this.draft.getClass().getSimpleName());
        }
        this.readystate = WebSocket.READYSTATE.OPEN;
        try {
            this.wsl.onWebsocketOpen(this, d);
        } catch (RuntimeException e) {
            this.wsl.onWebsocketError(this, e);
        }
    }

    @Override // org.java_websocket.WebSocket
    public boolean isConnecting() {
        if ($assertionsDisabled || !this.flushandclosestate || this.readystate == WebSocket.READYSTATE.CONNECTING) {
            return this.readystate == WebSocket.READYSTATE.CONNECTING;
        }
        throw new AssertionError();
    }

    @Override // org.java_websocket.WebSocket
    public boolean isOpen() {
        if (!$assertionsDisabled && this.readystate == WebSocket.READYSTATE.OPEN && this.flushandclosestate) {
            throw new AssertionError();
        }
        return this.readystate == WebSocket.READYSTATE.OPEN;
    }

    @Override // org.java_websocket.WebSocket
    public boolean isClosing() {
        return this.readystate == WebSocket.READYSTATE.CLOSING;
    }

    @Override // org.java_websocket.WebSocket
    public boolean isFlushAndClose() {
        return this.flushandclosestate;
    }

    @Override // org.java_websocket.WebSocket
    public boolean isClosed() {
        return this.readystate == WebSocket.READYSTATE.CLOSED;
    }

    @Override // org.java_websocket.WebSocket
    public WebSocket.READYSTATE getReadyState() {
        return this.readystate;
    }

    public int hashCode() {
        return super.hashCode();
    }

    public String toString() {
        return super.toString();
    }

    @Override // org.java_websocket.WebSocket
    public InetSocketAddress getRemoteSocketAddress() {
        return this.wsl.getRemoteSocketAddress(this);
    }

    @Override // org.java_websocket.WebSocket
    public InetSocketAddress getLocalSocketAddress() {
        return this.wsl.getLocalSocketAddress(this);
    }

    @Override // org.java_websocket.WebSocket
    public Draft getDraft() {
        return this.draft;
    }

    @Override // org.java_websocket.WebSocket
    public void close() {
        close(1000);
    }

    @Override // org.java_websocket.WebSocket
    public String getResourceDescriptor() {
        return this.resourceDescriptor;
    }

    long getLastPong() {
        return this.lastPong;
    }
}
