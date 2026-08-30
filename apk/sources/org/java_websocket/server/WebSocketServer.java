package org.java_websocket.server;

import java.io.IOException;
import java.lang.Thread;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.AbstractWebSocket;
import org.java_websocket.SocketChannelIOHelper;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketFactory;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.WrappedByteChannel;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

/* JADX INFO: loaded from: classes.dex */
public abstract class WebSocketServer extends AbstractWebSocket implements Runnable {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static int DECODERS;
    private final InetSocketAddress address;
    private BlockingQueue<ByteBuffer> buffers;
    private final Collection<WebSocket> connections;
    protected List<WebSocketWorker> decoders;
    private List<Draft> drafts;
    private List<WebSocketImpl> iqueue;
    private final AtomicBoolean isclosed;
    private int queueinvokes;
    private final AtomicInteger queuesize;
    private Selector selector;
    private Thread selectorthread;
    private ServerSocketChannel server;
    private WebSocketServerFactory wsf;

    public interface WebSocketServerFactory extends WebSocketFactory {
        void close();

        @Override // org.java_websocket.WebSocketFactory
        WebSocketImpl createWebSocket(WebSocketAdapter webSocketAdapter, List<Draft> list);

        @Override // org.java_websocket.WebSocketFactory
        WebSocketImpl createWebSocket(WebSocketAdapter webSocketAdapter, Draft draft);

        ByteChannel wrapChannel(SocketChannel socketChannel, SelectionKey selectionKey) throws IOException;
    }

    public abstract void onClose(WebSocket webSocket, int i, String str, boolean z);

    public abstract void onError(WebSocket webSocket, Exception exc);

    public abstract void onMessage(WebSocket webSocket, String str);

    public abstract void onOpen(WebSocket webSocket, ClientHandshake clientHandshake);

    public abstract void onStart();

    static {
        $assertionsDisabled = !WebSocketServer.class.desiredAssertionStatus();
        DECODERS = Runtime.getRuntime().availableProcessors();
    }

    public WebSocketServer() {
        this(new InetSocketAddress(80), DECODERS, null);
    }

    public WebSocketServer(InetSocketAddress address) {
        this(address, DECODERS, null);
    }

    public WebSocketServer(InetSocketAddress address, int decodercount) {
        this(address, decodercount, null);
    }

    public WebSocketServer(InetSocketAddress address, List<Draft> drafts) {
        this(address, DECODERS, drafts);
    }

    public WebSocketServer(InetSocketAddress address, int decodercount, List<Draft> drafts) {
        this(address, decodercount, drafts, new HashSet());
    }

    public WebSocketServer(InetSocketAddress address, int decodercount, List<Draft> drafts, Collection<WebSocket> connectionscontainer) {
        this.isclosed = new AtomicBoolean(false);
        this.queueinvokes = 0;
        this.queuesize = new AtomicInteger(0);
        this.wsf = new DefaultWebSocketServerFactory();
        if (address == null || decodercount < 1 || connectionscontainer == null) {
            throw new IllegalArgumentException("address and connectionscontainer must not be null and you need at least 1 decoder");
        }
        if (drafts == null) {
            this.drafts = Collections.emptyList();
        } else {
            this.drafts = drafts;
        }
        this.address = address;
        this.connections = connectionscontainer;
        setTcpNoDelay(false);
        this.iqueue = new LinkedList();
        this.decoders = new ArrayList(decodercount);
        this.buffers = new LinkedBlockingQueue();
        for (int i = 0; i < decodercount; i++) {
            WebSocketWorker ex = new WebSocketWorker();
            this.decoders.add(ex);
            ex.start();
        }
    }

    public void start() {
        if (this.selectorthread != null) {
            throw new IllegalStateException(getClass().getName() + " can only be started once.");
        }
        new Thread(this).start();
    }

    public void stop(int timeout) throws InterruptedException {
        List<WebSocket> socketsToClose;
        if (this.isclosed.compareAndSet(false, true)) {
            synchronized (this.connections) {
                socketsToClose = new ArrayList<>(this.connections);
            }
            for (WebSocket ws : socketsToClose) {
                ws.close(1001);
            }
            this.wsf.close();
            synchronized (this) {
                if (this.selectorthread != null && this.selectorthread != Thread.currentThread()) {
                    this.selectorthread.interrupt();
                    this.selector.wakeup();
                    this.selectorthread.join(timeout);
                }
            }
        }
    }

    public void stop() throws InterruptedException, IOException {
        stop(0);
    }

    @Override // org.java_websocket.AbstractWebSocket
    public Collection<WebSocket> connections() {
        return this.connections;
    }

    public InetSocketAddress getAddress() {
        return this.address;
    }

    public int getPort() {
        int port = getAddress().getPort();
        if (port == 0 && this.server != null) {
            return this.server.socket().getLocalPort();
        }
        return port;
    }

    public List<Draft> getDraft() {
        return Collections.unmodifiableList(this.drafts);
    }

    @Override // java.lang.Runnable
    public void run() {
        synchronized (this) {
            if (this.selectorthread != null) {
                throw new IllegalStateException(getClass().getName() + " can only be started once.");
            }
            this.selectorthread = Thread.currentThread();
            if (this.isclosed.get()) {
                return;
            }
            this.selectorthread.setName("WebsocketSelector" + this.selectorthread.getId());
            try {
                this.server = ServerSocketChannel.open();
                this.server.configureBlocking(false);
                ServerSocket socket = this.server.socket();
                socket.setReceiveBufferSize(WebSocketImpl.RCVBUF);
                socket.bind(this.address);
                this.selector = Selector.open();
                this.server.register(this.selector, this.server.validOps());
                startConnectionLostTimer();
                onStart();
                while (!this.selectorthread.isInterrupted()) {
                    try {
                        try {
                            SelectionKey key = null;
                            try {
                                try {
                                    this.selector.select();
                                    Set<SelectionKey> keys = this.selector.selectedKeys();
                                    Iterator<SelectionKey> i = keys.iterator();
                                    while (i.hasNext()) {
                                        SelectionKey key2 = i.next();
                                        if (key2.isValid()) {
                                            if (!key2.isAcceptable()) {
                                                if (key2.isReadable()) {
                                                    WebSocketImpl conn = (WebSocketImpl) key2.attachment();
                                                    ByteBuffer buf = takeBuffer();
                                                    if (conn.channel == null) {
                                                        if (key2 != null) {
                                                            key2.cancel();
                                                        }
                                                        handleIOException(key2, conn, new IOException());
                                                    } else {
                                                        try {
                                                            if (SocketChannelIOHelper.read(buf, conn, conn.channel) && buf.hasRemaining()) {
                                                                conn.inQueue.put(buf);
                                                                queue(conn);
                                                                i.remove();
                                                                if ((conn.channel instanceof WrappedByteChannel) && ((WrappedByteChannel) conn.channel).isNeedRead()) {
                                                                    this.iqueue.add(conn);
                                                                }
                                                            } else {
                                                                pushBuffer(buf);
                                                            }
                                                        } catch (IOException e) {
                                                            pushBuffer(buf);
                                                            throw e;
                                                        }
                                                    }
                                                }
                                                if (key2.isWritable()) {
                                                    WebSocketImpl conn2 = (WebSocketImpl) key2.attachment();
                                                    if (SocketChannelIOHelper.batch(conn2, conn2.channel) && key2.isValid()) {
                                                        key2.interestOps(1);
                                                    }
                                                }
                                            } else if (onConnect(key2)) {
                                                SocketChannel channel = this.server.accept();
                                                if (channel != null) {
                                                    channel.configureBlocking(false);
                                                    Socket socket2 = channel.socket();
                                                    socket2.setTcpNoDelay(isTcpNoDelay());
                                                    socket2.setKeepAlive(true);
                                                    WebSocketImpl w = this.wsf.createWebSocket((WebSocketAdapter) this, this.drafts);
                                                    w.key = channel.register(this.selector, 1, w);
                                                    try {
                                                        w.channel = this.wsf.wrapChannel(channel, w.key);
                                                        i.remove();
                                                        allocateBuffers(w);
                                                    } catch (IOException ex) {
                                                        if (w.key != null) {
                                                            w.key.cancel();
                                                        }
                                                        handleIOException(w.key, null, ex);
                                                    }
                                                }
                                            } else {
                                                key2.cancel();
                                            }
                                        }
                                    }
                                    while (!this.iqueue.isEmpty()) {
                                        WebSocketImpl conn3 = this.iqueue.remove(0);
                                        WrappedByteChannel c = (WrappedByteChannel) conn3.channel;
                                        ByteBuffer buf2 = takeBuffer();
                                        try {
                                            if (SocketChannelIOHelper.readMore(buf2, conn3, c)) {
                                                this.iqueue.add(conn3);
                                            }
                                            if (buf2.hasRemaining()) {
                                                conn3.inQueue.put(buf2);
                                                queue(conn3);
                                            } else {
                                                pushBuffer(buf2);
                                            }
                                        } catch (IOException e2) {
                                            pushBuffer(buf2);
                                            throw e2;
                                        }
                                    }
                                } catch (IOException ex2) {
                                    if (0 != 0) {
                                        key.cancel();
                                    }
                                    handleIOException(null, null, ex2);
                                }
                            } catch (InterruptedException e3) {
                                stopConnectionLostTimer();
                                if (this.decoders != null) {
                                    for (WebSocketWorker w2 : this.decoders) {
                                        w2.interrupt();
                                    }
                                }
                                if (this.selector != null) {
                                    try {
                                        this.selector.close();
                                    } catch (IOException e4) {
                                        onError(null, e4);
                                    }
                                }
                                if (this.server != null) {
                                    try {
                                    } catch (IOException e5) {
                                        e = e5;
                                    }
                                    try {
                                        this.server.close();
                                        return;
                                    } catch (IOException e6) {
                                        e = e6;
                                        onError(null, e);
                                    }
                                }
                                return;
                            } catch (CancelledKeyException e7) {
                            } catch (ClosedByInterruptException e8) {
                                stopConnectionLostTimer();
                                if (this.decoders != null) {
                                    for (WebSocketWorker w3 : this.decoders) {
                                        w3.interrupt();
                                    }
                                }
                                if (this.selector != null) {
                                    try {
                                        this.selector.close();
                                    } catch (IOException e9) {
                                        onError(null, e9);
                                    }
                                }
                                if (this.server == null) {
                                    return;
                                }
                                try {
                                    try {
                                        this.server.close();
                                        return;
                                    } catch (IOException e10) {
                                        e = e10;
                                        onError(null, e);
                                    }
                                } catch (IOException e11) {
                                    e = e11;
                                }
                            }
                        } catch (RuntimeException e12) {
                            handleFatal(null, e12);
                            stopConnectionLostTimer();
                            if (this.decoders != null) {
                                for (WebSocketWorker w4 : this.decoders) {
                                    w4.interrupt();
                                }
                            }
                            if (this.selector != null) {
                                try {
                                    this.selector.close();
                                } catch (IOException e13) {
                                    onError(null, e13);
                                }
                            }
                            if (this.server != null) {
                                try {
                                    this.server.close();
                                    return;
                                } catch (IOException e14) {
                                    e = e14;
                                    onError(null, e);
                                }
                            }
                            return;
                        }
                    } finally {
                    }
                }
                stopConnectionLostTimer();
                if (this.decoders != null) {
                    for (WebSocketWorker w5 : this.decoders) {
                        w5.interrupt();
                    }
                }
                if (this.selector != null) {
                    try {
                        this.selector.close();
                    } catch (IOException e15) {
                        onError(null, e15);
                    }
                }
                if (this.server != null) {
                    try {
                    } catch (IOException e16) {
                        e = e16;
                    }
                    try {
                        this.server.close();
                    } catch (IOException e17) {
                        e = e17;
                        onError(null, e);
                    }
                }
            } catch (IOException ex3) {
                handleFatal(null, ex3);
                if (this.decoders != null) {
                    for (WebSocketWorker w6 : this.decoders) {
                        w6.interrupt();
                    }
                }
            }
        }
    }

    protected void allocateBuffers(WebSocket c) throws InterruptedException {
        if (this.queuesize.get() < (this.decoders.size() * 2) + 1) {
            this.queuesize.incrementAndGet();
            this.buffers.put(createBuffer());
        }
    }

    protected void releaseBuffers(WebSocket c) throws InterruptedException {
    }

    public ByteBuffer createBuffer() {
        return ByteBuffer.allocate(WebSocketImpl.RCVBUF);
    }

    protected void queue(WebSocketImpl ws) throws InterruptedException {
        if (ws.workerThread == null) {
            ws.workerThread = this.decoders.get(this.queueinvokes % this.decoders.size());
            this.queueinvokes++;
        }
        ws.workerThread.put(ws);
    }

    private ByteBuffer takeBuffer() throws InterruptedException {
        return this.buffers.take();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pushBuffer(ByteBuffer buf) throws InterruptedException {
        if (this.buffers.size() <= this.queuesize.intValue()) {
            this.buffers.put(buf);
        }
    }

    private void handleIOException(SelectionKey key, WebSocket conn, IOException ex) {
        SelectableChannel channel;
        if (conn != null) {
            conn.closeConnection(1006, ex.getMessage());
            return;
        }
        if (key != null && (channel = key.channel()) != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
            }
            if (WebSocketImpl.DEBUG) {
                System.out.println("Connection closed because of " + ex);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleFatal(WebSocket conn, Exception e) {
        onError(conn, e);
        try {
            stop();
        } catch (IOException e1) {
            onError(null, e1);
        } catch (InterruptedException e12) {
            Thread.currentThread().interrupt();
            onError(null, e12);
        }
    }

    @Override // org.java_websocket.WebSocketListener
    public final void onWebsocketMessage(WebSocket conn, String message) {
        onMessage(conn, message);
    }

    @Override // org.java_websocket.WebSocketAdapter, org.java_websocket.WebSocketListener
    @Deprecated
    public void onWebsocketMessageFragment(WebSocket conn, Framedata frame) {
        onFragment(conn, frame);
    }

    @Override // org.java_websocket.WebSocketListener
    public final void onWebsocketMessage(WebSocket conn, ByteBuffer blob) {
        onMessage(conn, blob);
    }

    @Override // org.java_websocket.WebSocketListener
    public final void onWebsocketOpen(WebSocket conn, Handshakedata handshake) {
        if (addConnection(conn)) {
            onOpen(conn, (ClientHandshake) handshake);
        }
    }

    @Override // org.java_websocket.WebSocketListener
    public final void onWebsocketClose(WebSocket conn, int code, String reason, boolean remote) {
        this.selector.wakeup();
        try {
            if (removeConnection(conn)) {
                onClose(conn, code, reason, remote);
            }
        } finally {
            try {
                releaseBuffers(conn);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected boolean removeConnection(WebSocket ws) {
        boolean removed;
        synchronized (this.connections) {
            removed = this.connections.remove(ws);
            if (!$assertionsDisabled && !removed) {
                throw new AssertionError();
            }
        }
        if (this.isclosed.get() && this.connections.size() == 0) {
            this.selectorthread.interrupt();
        }
        return removed;
    }

    @Override // org.java_websocket.WebSocketAdapter, org.java_websocket.WebSocketListener
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
        return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
    }

    protected boolean addConnection(WebSocket ws) {
        boolean succ;
        if (!this.isclosed.get()) {
            synchronized (this.connections) {
                succ = this.connections.add(ws);
                if (!$assertionsDisabled && !succ) {
                    throw new AssertionError();
                }
            }
            return succ;
        }
        ws.close(1001);
        return true;
    }

    @Override // org.java_websocket.WebSocketListener
    public final void onWebsocketError(WebSocket conn, Exception ex) {
        onError(conn, ex);
    }

    @Override // org.java_websocket.WebSocketListener
    public final void onWriteDemand(WebSocket w) {
        WebSocketImpl conn = (WebSocketImpl) w;
        try {
            conn.key.interestOps(5);
        } catch (CancelledKeyException e) {
            conn.outQueue.clear();
        }
        this.selector.wakeup();
    }

    @Override // org.java_websocket.WebSocketListener
    public void onWebsocketCloseInitiated(WebSocket conn, int code, String reason) {
        onCloseInitiated(conn, code, reason);
    }

    @Override // org.java_websocket.WebSocketListener
    public void onWebsocketClosing(WebSocket conn, int code, String reason, boolean remote) {
        onClosing(conn, code, reason, remote);
    }

    public void onCloseInitiated(WebSocket conn, int code, String reason) {
    }

    public void onClosing(WebSocket conn, int code, String reason, boolean remote) {
    }

    public final void setWebSocketFactory(WebSocketServerFactory wsf) {
        this.wsf = wsf;
    }

    public final WebSocketFactory getWebSocketFactory() {
        return this.wsf;
    }

    protected boolean onConnect(SelectionKey key) {
        return true;
    }

    private Socket getSocket(WebSocket conn) {
        WebSocketImpl impl = (WebSocketImpl) conn;
        return ((SocketChannel) impl.key.channel()).socket();
    }

    @Override // org.java_websocket.WebSocketListener
    public InetSocketAddress getLocalSocketAddress(WebSocket conn) {
        return (InetSocketAddress) getSocket(conn).getLocalSocketAddress();
    }

    @Override // org.java_websocket.WebSocketListener
    public InetSocketAddress getRemoteSocketAddress(WebSocket conn) {
        return (InetSocketAddress) getSocket(conn).getRemoteSocketAddress();
    }

    public void onMessage(WebSocket conn, ByteBuffer message) {
    }

    public void onFragment(WebSocket conn, Framedata fragment) {
    }

    public class WebSocketWorker extends Thread {
        static final /* synthetic */ boolean $assertionsDisabled;
        private BlockingQueue<WebSocketImpl> iqueue = new LinkedBlockingQueue();

        static {
            $assertionsDisabled = !WebSocketServer.class.desiredAssertionStatus();
        }

        public WebSocketWorker() {
            setName("WebSocketWorker-" + getId());
            setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() { // from class: org.java_websocket.server.WebSocketServer.WebSocketWorker.1
                @Override // java.lang.Thread.UncaughtExceptionHandler
                public void uncaughtException(Thread t, Throwable e) {
                    System.err.print("Uncaught exception in thread \"" + t.getName() + "\":");
                    e.printStackTrace(System.err);
                }
            });
        }

        public void put(WebSocketImpl ws) throws InterruptedException {
            this.iqueue.put(ws);
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            WebSocketImpl ws = null;
            while (true) {
                try {
                    ws = this.iqueue.take();
                    ByteBuffer buf = ws.inQueue.poll();
                    if (!$assertionsDisabled && buf == null) {
                        throw new AssertionError();
                    }
                    try {
                        try {
                            ws.decode(buf);
                        } catch (Exception e) {
                            System.err.println("Error while reading from remote connection: " + e);
                            e.printStackTrace();
                            WebSocketServer.this.pushBuffer(buf);
                        }
                    } finally {
                        WebSocketServer.this.pushBuffer(buf);
                    }
                } catch (InterruptedException e2) {
                    return;
                } catch (RuntimeException e3) {
                    WebSocketServer.this.handleFatal(ws, e3);
                    return;
                }
            }
        }
    }
}
