package com.koushikdutta.async;

import android.os.Build;
import android.os.Handler;
import android.util.Log;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.ConnectCallback;
import com.koushikdutta.async.callback.ListenCallback;
import com.koushikdutta.async.future.Cancellable;
import com.koushikdutta.async.future.Future;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.async.future.SimpleFuture;
import com.koushikdutta.async.future.TransformFuture;
import com.koushikdutta.async.util.StreamUtility;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: classes.dex */
public class AsyncServer {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static final String LOGTAG = "NIO";
    private static final long QUEUE_EMPTY = Long.MAX_VALUE;
    private static final Comparator<InetAddress> ipSorter;
    static AsyncServer mInstance;
    static final WeakHashMap<Thread, AsyncServer> mServers;
    private static ExecutorService synchronousResolverWorkers;
    private static ExecutorService synchronousWorkers;
    Thread mAffinity;
    String mName;
    PriorityQueue<Scheduled> mQueue;
    private SelectorWrapper mSelector;
    int postCounter;

    static {
        $assertionsDisabled = !AsyncServer.class.desiredAssertionStatus();
        try {
            if (Build.VERSION.SDK_INT <= 8) {
                System.setProperty("java.net.preferIPv4Stack", "true");
                System.setProperty("java.net.preferIPv6Addresses", "false");
            }
        } catch (Throwable th) {
        }
        mInstance = new AsyncServer();
        synchronousWorkers = newSynchronousWorkers("AsyncServer-worker-");
        ipSorter = new Comparator<InetAddress>() { // from class: com.koushikdutta.async.AsyncServer.8
            @Override // java.util.Comparator
            public int compare(InetAddress lhs, InetAddress rhs) {
                if ((lhs instanceof Inet4Address) && (rhs instanceof Inet4Address)) {
                    return 0;
                }
                if ((lhs instanceof Inet6Address) && (rhs instanceof Inet6Address)) {
                    return 0;
                }
                if ((lhs instanceof Inet4Address) && (rhs instanceof Inet6Address)) {
                    return -1;
                }
                return 1;
            }
        };
        synchronousResolverWorkers = newSynchronousWorkers("AsyncServer-resolver-");
        mServers = new WeakHashMap<>();
    }

    private static class RunnableWrapper implements Runnable {
        Handler handler;
        boolean hasRun;
        Runnable runnable;
        ThreadQueue threadQueue;

        private RunnableWrapper() {
        }

        /* JADX WARN: Multi-variable type inference failed */
        @Override // java.lang.Runnable
        public void run() {
            synchronized (this) {
                if (!this.hasRun) {
                    this.hasRun = true;
                    try {
                        this.runnable.run();
                    } finally {
                        this.threadQueue.remove(this);
                        this.handler.removeCallbacks(this);
                        this.threadQueue = null;
                        this.handler = null;
                        this.runnable = null;
                    }
                }
            }
        }
    }

    public static void post(Handler handler, Runnable runnable) {
        RunnableWrapper wrapper = new RunnableWrapper();
        ThreadQueue threadQueue = ThreadQueue.getOrCreateThreadQueue(handler.getLooper().getThread());
        wrapper.threadQueue = threadQueue;
        wrapper.handler = handler;
        wrapper.runnable = runnable;
        threadQueue.add((Runnable) wrapper);
        handler.post(wrapper);
        threadQueue.queueSemaphore.release();
    }

    public static AsyncServer getDefault() {
        return mInstance;
    }

    public boolean isRunning() {
        return this.mSelector != null;
    }

    public AsyncServer() {
        this(null);
    }

    public AsyncServer(String name) {
        this.postCounter = 0;
        this.mQueue = new PriorityQueue<>(1, Scheduler.INSTANCE);
        this.mName = name == null ? "AsyncServer" : name;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleSocket(AsyncNetworkSocket handler) throws ClosedChannelException {
        ChannelWrapper sc = handler.getChannel();
        SelectionKey ckey = sc.register(this.mSelector.getSelector());
        ckey.attach(handler);
        handler.setup(this, ckey);
    }

    public void removeAllCallbacks(Object scheduled) {
        synchronized (this) {
            this.mQueue.remove(scheduled);
        }
    }

    private static void wakeup(final SelectorWrapper selector) {
        synchronousWorkers.execute(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    selector.wakeupOnce();
                } catch (Exception e) {
                    Log.i(AsyncServer.LOGTAG, "Selector Exception? L Preview?");
                }
            }
        });
    }

    public Object postDelayed(Runnable runnable, long delay) {
        long time;
        Scheduled s;
        synchronized (this) {
            if (delay > 0) {
                time = System.currentTimeMillis() + delay;
            } else if (delay == 0) {
                int i = this.postCounter;
                this.postCounter = i + 1;
                time = i;
            } else if (this.mQueue.size() > 0) {
                time = Math.min(0L, this.mQueue.peek().time - 1);
            } else {
                time = 0;
            }
            PriorityQueue<Scheduled> priorityQueue = this.mQueue;
            s = new Scheduled(runnable, time);
            priorityQueue.add(s);
            if (this.mSelector == null) {
                run(true);
            }
            if (!isAffinityThread()) {
                wakeup(this.mSelector);
            }
        }
        return s;
    }

    public Object postImmediate(Runnable runnable) {
        if (Thread.currentThread() != getAffinity()) {
            return postDelayed(runnable, -1L);
        }
        runnable.run();
        return null;
    }

    public Object post(Runnable runnable) {
        return postDelayed(runnable, 0L);
    }

    public Object post(final CompletedCallback callback, final Exception e) {
        return post(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.2
            @Override // java.lang.Runnable
            public void run() {
                callback.onCompleted(e);
            }
        });
    }

    public void run(final Runnable runnable) {
        if (Thread.currentThread() == this.mAffinity) {
            post(runnable);
            lockAndRunQueue(this, this.mQueue);
            return;
        }
        final Semaphore semaphore = new Semaphore(0);
        post(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.3
            @Override // java.lang.Runnable
            public void run() {
                runnable.run();
                semaphore.release();
            }
        });
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Log.e(LOGTAG, "run", e);
        }
    }

    private static class Scheduled {
        public Runnable runnable;
        public long time;

        public Scheduled(Runnable runnable, long time) {
            this.runnable = runnable;
            this.time = time;
        }
    }

    static class Scheduler implements Comparator<Scheduled> {
        public static Scheduler INSTANCE = new Scheduler();

        private Scheduler() {
        }

        @Override // java.util.Comparator
        public int compare(Scheduled s1, Scheduled s2) {
            if (s1.time == s2.time) {
                return 0;
            }
            if (s1.time > s2.time) {
                return 1;
            }
            return -1;
        }
    }

    public void stop() {
        synchronized (this) {
            boolean isAffinityThread = isAffinityThread();
            final SelectorWrapper currentSelector = this.mSelector;
            if (currentSelector != null) {
                synchronized (mServers) {
                    mServers.remove(this.mAffinity);
                }
                final Semaphore semaphore = new Semaphore(0);
                this.mQueue.add(new Scheduled(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.4
                    @Override // java.lang.Runnable
                    public void run() {
                        AsyncServer.shutdownEverything(currentSelector);
                        semaphore.release();
                    }
                }, 0L));
                currentSelector.wakeupOnce();
                shutdownKeys(currentSelector);
                this.mQueue = new PriorityQueue<>(1, Scheduler.INSTANCE);
                this.mSelector = null;
                this.mAffinity = null;
                if (!isAffinityThread) {
                    try {
                        semaphore.acquire();
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    protected void onDataReceived(int transmitted) {
    }

    protected void onDataSent(int transmitted) {
    }

    private static class ObjectHolder<T> {
        T held;

        private ObjectHolder() {
        }
    }

    public AsyncServerSocket listen(final InetAddress host, final int port, final ListenCallback handler) {
        final ObjectHolder<AsyncServerSocket> holder = new ObjectHolder<>();
        run(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.5
            /* JADX WARN: Type inference failed for: r8v11, types: [T, com.koushikdutta.async.AsyncServer$5$1] */
            @Override // java.lang.Runnable
            public void run() {
                InetSocketAddress isa;
                final ServerSocketChannel closeableServer = null;
                ServerSocketChannelWrapper closeableWrapper = null;
                try {
                    closeableServer = ServerSocketChannel.open();
                    final ServerSocketChannelWrapper closeableWrapper2 = new ServerSocketChannelWrapper(closeableServer);
                    try {
                        if (host == null) {
                            isa = new InetSocketAddress(port);
                        } else {
                            isa = new InetSocketAddress(host, port);
                        }
                        closeableServer.socket().bind(isa);
                        final SelectionKey key = closeableWrapper2.register(AsyncServer.this.mSelector.getSelector());
                        key.attach(handler);
                        ListenCallback listenCallback = handler;
                        ObjectHolder objectHolder = holder;
                        ?? r8 = new AsyncServerSocket() { // from class: com.koushikdutta.async.AsyncServer.5.1
                            @Override // com.koushikdutta.async.AsyncServerSocket
                            public int getLocalPort() {
                                return closeableServer.socket().getLocalPort();
                            }

                            @Override // com.koushikdutta.async.AsyncServerSocket
                            public void stop() {
                                StreamUtility.closeQuietly(closeableWrapper2);
                                try {
                                    key.cancel();
                                } catch (Exception e) {
                                }
                            }
                        };
                        objectHolder.held = r8;
                        listenCallback.onListening((AsyncServerSocket) r8);
                    } catch (IOException e) {
                        e = e;
                        closeableWrapper = closeableWrapper2;
                        Log.e(AsyncServer.LOGTAG, "wtf", e);
                        StreamUtility.closeQuietly(closeableWrapper, closeableServer);
                        handler.onCompleted(e);
                    }
                } catch (IOException e2) {
                    e = e2;
                }
            }
        });
        return holder.held;
    }

    private class ConnectFuture extends SimpleFuture<AsyncNetworkSocket> {
        ConnectCallback callback;
        SocketChannel socket;

        private ConnectFuture() {
        }

        @Override // com.koushikdutta.async.future.SimpleCancellable
        protected void cancelCleanup() {
            super.cancelCleanup();
            try {
                if (this.socket != null) {
                    this.socket.close();
                }
            } catch (IOException e) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public ConnectFuture connectResolvedInetSocketAddress(final InetSocketAddress address, final ConnectCallback callback) {
        final ConnectFuture cancel = new ConnectFuture();
        if (!$assertionsDisabled && address.isUnresolved()) {
            throw new AssertionError();
        }
        post(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.6
            @Override // java.lang.Runnable
            public void run() {
                SocketChannel socket;
                if (!cancel.isCancelled()) {
                    cancel.callback = callback;
                    SelectionKey ckey = null;
                    SocketChannel socket2 = null;
                    try {
                        ConnectFuture connectFuture = cancel;
                        socket = SocketChannel.open();
                        connectFuture.socket = socket;
                    } catch (Throwable th) {
                        e = th;
                    }
                    try {
                        socket.configureBlocking(false);
                        ckey = socket.register(AsyncServer.this.mSelector.getSelector(), 8);
                        ckey.attach(cancel);
                        socket.connect(address);
                    } catch (Throwable th2) {
                        e = th2;
                        socket2 = socket;
                        if (ckey != null) {
                            ckey.cancel();
                        }
                        StreamUtility.closeQuietly(socket2);
                        cancel.setComplete((Exception) new RuntimeException(e));
                    }
                }
            }
        });
        return cancel;
    }

    public Cancellable connectSocket(final InetSocketAddress remote, final ConnectCallback callback) {
        if (!remote.isUnresolved()) {
            return connectResolvedInetSocketAddress(remote, callback);
        }
        final SimpleFuture<AsyncNetworkSocket> ret = new SimpleFuture<>();
        Future<InetAddress> lookup = getByName(remote.getHostName());
        ret.setParent((Cancellable) lookup);
        lookup.setCallback(new FutureCallback<InetAddress>() { // from class: com.koushikdutta.async.AsyncServer.7
            @Override // com.koushikdutta.async.future.FutureCallback
            public void onCompleted(Exception e, InetAddress result) {
                if (e == null) {
                    ret.setComplete((Future) AsyncServer.this.connectResolvedInetSocketAddress(new InetSocketAddress(result, remote.getPort()), callback));
                } else {
                    callback.onConnectCompleted(e, null);
                    ret.setComplete(e);
                }
            }
        });
        return ret;
    }

    public Cancellable connectSocket(String host, int port, ConnectCallback callback) {
        return connectSocket(InetSocketAddress.createUnresolved(host, port), callback);
    }

    private static ExecutorService newSynchronousWorkers(String prefix) {
        ThreadFactory tf = new NamedThreadFactory(prefix);
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, 4, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue(), tf);
        return tpe;
    }

    public Future<InetAddress[]> getAllByName(final String host) {
        final SimpleFuture<InetAddress[]> ret = new SimpleFuture<>();
        synchronousResolverWorkers.execute(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.9
            @Override // java.lang.Runnable
            public void run() {
                try {
                    final InetAddress[] result = InetAddress.getAllByName(host);
                    Arrays.sort(result, AsyncServer.ipSorter);
                    if (result == null || result.length == 0) {
                        throw new HostnameResolutionException("no addresses for host");
                    }
                    AsyncServer.this.post(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.9.1
                        @Override // java.lang.Runnable
                        public void run() {
                            ret.setComplete(null, result);
                        }
                    });
                } catch (Exception e) {
                    AsyncServer.this.post(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.9.2
                        @Override // java.lang.Runnable
                        public void run() {
                            ret.setComplete(e, null);
                        }
                    });
                }
            }
        });
        return ret;
    }

    public Future<InetAddress> getByName(String host) {
        return (Future) getAllByName(host).then(new TransformFuture<InetAddress, InetAddress[]>() { // from class: com.koushikdutta.async.AsyncServer.10
            /* JADX INFO: Access modifiers changed from: protected */
            @Override // com.koushikdutta.async.future.TransformFuture
            public void transform(InetAddress[] result) throws Exception {
                setComplete(result[0]);
            }
        });
    }

    public AsyncDatagramSocket connectDatagram(final String host, final int port) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        handler.attach(socket);
        run(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.11
            @Override // java.lang.Runnable
            public void run() {
                try {
                    SocketAddress remote = new InetSocketAddress(host, port);
                    AsyncServer.this.handleSocket(handler);
                    socket.connect(remote);
                } catch (IOException e) {
                    Log.e(AsyncServer.LOGTAG, "Datagram error", e);
                    StreamUtility.closeQuietly(socket);
                }
            }
        });
        return handler;
    }

    public AsyncDatagramSocket openDatagram() throws IOException {
        return openDatagram(null, false);
    }

    public AsyncDatagramSocket openDatagram(final SocketAddress address, final boolean reuseAddress) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        handler.attach(socket);
        run(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.12
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (reuseAddress) {
                        socket.socket().setReuseAddress(reuseAddress);
                    }
                    socket.socket().bind(address);
                    AsyncServer.this.handleSocket(handler);
                } catch (IOException e) {
                    Log.e(AsyncServer.LOGTAG, "Datagram error", e);
                    StreamUtility.closeQuietly(socket);
                }
            }
        });
        return handler;
    }

    public AsyncDatagramSocket connectDatagram(final SocketAddress remote) throws IOException {
        final DatagramChannel socket = DatagramChannel.open();
        final AsyncDatagramSocket handler = new AsyncDatagramSocket();
        handler.attach(socket);
        run(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.13
            @Override // java.lang.Runnable
            public void run() {
                try {
                    AsyncServer.this.handleSocket(handler);
                    socket.connect(remote);
                } catch (IOException e) {
                    StreamUtility.closeQuietly(socket);
                }
            }
        });
        return handler;
    }

    private boolean addMe() {
        synchronized (mServers) {
            AsyncServer current = mServers.get(this.mAffinity);
            if (current != null) {
                return false;
            }
            mServers.put(this.mAffinity, this);
            return true;
        }
    }

    public static AsyncServer getCurrentThreadServer() {
        return mServers.get(Thread.currentThread());
    }

    private void run(boolean newThread) {
        final SelectorWrapper selector;
        final PriorityQueue<Scheduled> queue;
        boolean reentrant = false;
        synchronized (this) {
            if (this.mSelector != null) {
                Log.i(LOGTAG, "Reentrant call");
                if (!$assertionsDisabled && Thread.currentThread() != this.mAffinity) {
                    throw new AssertionError();
                }
                reentrant = true;
                selector = this.mSelector;
                queue = this.mQueue;
            } else {
                try {
                    selector = new SelectorWrapper(SelectorProvider.provider().openSelector());
                    this.mSelector = selector;
                    queue = this.mQueue;
                    if (newThread) {
                        this.mAffinity = new Thread(this.mName) { // from class: com.koushikdutta.async.AsyncServer.14
                            @Override // java.lang.Thread, java.lang.Runnable
                            public void run() {
                                AsyncServer.run(AsyncServer.this, selector, queue);
                            }
                        };
                    } else {
                        this.mAffinity = Thread.currentThread();
                    }
                    if (!addMe()) {
                        try {
                            this.mSelector.close();
                        } catch (Exception e) {
                        }
                        this.mSelector = null;
                        this.mAffinity = null;
                        return;
                    } else if (newThread) {
                        this.mAffinity.start();
                        return;
                    }
                } catch (IOException e2) {
                    return;
                }
            }
            if (reentrant) {
                try {
                    runLoop(this, selector, queue);
                    return;
                } catch (AsyncSelectorException e3) {
                    Log.i(LOGTAG, "Selector closed", e3);
                    try {
                        selector.getSelector().close();
                        return;
                    } catch (Exception e4) {
                        return;
                    }
                }
            }
            run(this, selector, queue);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void run(AsyncServer server, SelectorWrapper selector, PriorityQueue<Scheduled> queue) {
        while (true) {
            try {
                runLoop(server, selector, queue);
            } catch (AsyncSelectorException e) {
                Log.i(LOGTAG, "Selector exception, shutting down", e);
                try {
                    selector.getSelector().close();
                } catch (Exception e2) {
                }
            }
            synchronized (server) {
                if (!selector.isOpen() || (selector.keys().size() <= 0 && queue.size() <= 0)) {
                    break;
                }
            }
        }
        shutdownEverything(selector);
        if (server.mSelector == selector) {
            server.mQueue = new PriorityQueue<>(1, Scheduler.INSTANCE);
            server.mSelector = null;
            server.mAffinity = null;
        }
        synchronized (mServers) {
            mServers.remove(Thread.currentThread());
        }
    }

    private static void shutdownKeys(SelectorWrapper selector) {
        try {
            for (SelectionKey key : selector.keys()) {
                StreamUtility.closeQuietly(key.channel());
                try {
                    key.cancel();
                } catch (Exception e) {
                }
            }
        } catch (Exception e2) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void shutdownEverything(SelectorWrapper selector) {
        shutdownKeys(selector);
        try {
            selector.close();
        } catch (Exception e) {
        }
    }

    private static long lockAndRunQueue(AsyncServer server, PriorityQueue<Scheduled> queue) {
        long wait = QUEUE_EMPTY;
        while (true) {
            Scheduled run = null;
            synchronized (server) {
                long now = System.currentTimeMillis();
                if (queue.size() > 0) {
                    Scheduled s = queue.remove();
                    if (s.time <= now) {
                        run = s;
                    } else {
                        wait = s.time - now;
                        queue.add(s);
                    }
                }
            }
            if (run != null) {
                run.runnable.run();
            } else {
                server.postCounter = 0;
                return wait;
            }
        }
    }

    private static class AsyncSelectorException extends IOException {
        public AsyncSelectorException(Exception e) {
            super(e);
        }
    }

    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:593)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    private static void runLoop(AsyncServer server, SelectorWrapper selector, PriorityQueue<Scheduled> queue) throws AsyncSelectorException {
        boolean needsSelect = true;
        long wait = lockAndRunQueue(server, queue);
        try {
            synchronized (server) {
                int readyNow = selector.selectNow();
                if (readyNow == 0) {
                    if (selector.keys().size() == 0 && wait == QUEUE_EMPTY) {
                        return;
                    }
                } else {
                    needsSelect = false;
                }
                if (needsSelect) {
                    if (wait == QUEUE_EMPTY) {
                        selector.select();
                    } else {
                        selector.select(wait);
                    }
                }
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : readyKeys) {
                    try {
                        if (selectionKey.isAcceptable()) {
                            ServerSocketChannel nextReady = (ServerSocketChannel) selectionKey.channel();
                            SocketChannel sc = null;
                            SelectionKey ckey = null;
                            try {
                                sc = nextReady.accept();
                                if (sc != null) {
                                    sc.configureBlocking(false);
                                    SelectionKey selectionKeyRegister = sc.register(selector.getSelector(), 1);
                                    ListenCallback listenCallback = (ListenCallback) selectionKey.attachment();
                                    AsyncNetworkSocket asyncNetworkSocket = new AsyncNetworkSocket();
                                    asyncNetworkSocket.attach(sc, (InetSocketAddress) sc.socket().getRemoteSocketAddress());
                                    asyncNetworkSocket.setup(server, selectionKeyRegister);
                                    selectionKeyRegister.attach(asyncNetworkSocket);
                                    listenCallback.onAccepted(asyncNetworkSocket);
                                }
                            } catch (IOException e) {
                                StreamUtility.closeQuietly(sc);
                                if (0 != 0) {
                                    ckey.cancel();
                                }
                            }
                        } else if (selectionKey.isReadable()) {
                            AsyncNetworkSocket handler = (AsyncNetworkSocket) selectionKey.attachment();
                            int transmitted = handler.onReadable();
                            server.onDataReceived(transmitted);
                        } else if (selectionKey.isWritable()) {
                            AsyncNetworkSocket handler2 = (AsyncNetworkSocket) selectionKey.attachment();
                            handler2.onDataWritable();
                        } else if (selectionKey.isConnectable()) {
                            ConnectFuture cancel = (ConnectFuture) selectionKey.attachment();
                            SocketChannel sc2 = (SocketChannel) selectionKey.channel();
                            selectionKey.interestOps(1);
                            try {
                                sc2.finishConnect();
                                AsyncNetworkSocket asyncNetworkSocket2 = new AsyncNetworkSocket();
                                asyncNetworkSocket2.setup(server, selectionKey);
                                asyncNetworkSocket2.attach(sc2, (InetSocketAddress) sc2.socket().getRemoteSocketAddress());
                                selectionKey.attach(asyncNetworkSocket2);
                                try {
                                    if (cancel.setComplete(asyncNetworkSocket2)) {
                                        cancel.callback.onConnectCompleted(null, asyncNetworkSocket2);
                                    }
                                } catch (Exception e2) {
                                    throw new RuntimeException(e2);
                                }
                            } catch (IOException ex) {
                                selectionKey.cancel();
                                StreamUtility.closeQuietly(sc2);
                                if (cancel.setComplete((Exception) ex)) {
                                    cancel.callback.onConnectCompleted(ex, null);
                                }
                            }
                        } else {
                            Log.i(LOGTAG, "wtf");
                            throw new RuntimeException("Unknown key state.");
                        }
                    } catch (CancelledKeyException e3) {
                    }
                }
                readyKeys.clear();
            }
        } catch (Exception e4) {
            throw new AsyncSelectorException(e4);
        }
    }

    public void dump() {
        post(new Runnable() { // from class: com.koushikdutta.async.AsyncServer.15
            @Override // java.lang.Runnable
            public void run() {
                if (AsyncServer.this.mSelector == null) {
                    Log.i(AsyncServer.LOGTAG, "Server dump not possible. No selector?");
                    return;
                }
                Log.i(AsyncServer.LOGTAG, "Key Count: " + AsyncServer.this.mSelector.keys().size());
                for (SelectionKey key : AsyncServer.this.mSelector.keys()) {
                    Log.i(AsyncServer.LOGTAG, "Key: " + key);
                }
            }
        });
    }

    public Thread getAffinity() {
        return this.mAffinity;
    }

    public boolean isAffinityThread() {
        return this.mAffinity == Thread.currentThread();
    }

    public boolean isAffinityThreadOrStopped() {
        Thread affinity = this.mAffinity;
        return affinity == null || affinity == Thread.currentThread();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            this.group = s != null ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override // java.util.concurrent.ThreadFactory
        public Thread newThread(Runnable r) {
            Thread t = new Thread(this.group, r, this.namePrefix + this.threadNumber.getAndIncrement(), 0L);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != 5) {
                t.setPriority(5);
            }
            return t;
        }
    }
}
