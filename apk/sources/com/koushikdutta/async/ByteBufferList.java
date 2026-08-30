package com.koushikdutta.async;

import android.annotation.TargetApi;
import android.os.Looper;
import com.koushikdutta.async.util.Charsets;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.PriorityQueue;

/* JADX INFO: loaded from: classes.dex */
@TargetApi(9)
public class ByteBufferList {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static final ByteBuffer EMPTY_BYTEBUFFER;
    private static final Object LOCK;
    public static int MAX_ITEM_SIZE;
    private static int MAX_SIZE;
    static int currentSize;
    static int maxItem;
    static PriorityQueue<ByteBuffer> reclaimed;
    ArrayDeque<ByteBuffer> mBuffers = new ArrayDeque<>();
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    private int remaining = 0;

    static {
        $assertionsDisabled = !ByteBufferList.class.desiredAssertionStatus();
        reclaimed = new PriorityQueue<>(8, new Reclaimer());
        MAX_SIZE = 1048576;
        MAX_ITEM_SIZE = 262144;
        currentSize = 0;
        maxItem = 0;
        LOCK = new Object();
        EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    }

    public ByteOrder order() {
        return this.order;
    }

    public ByteBufferList order(ByteOrder order) {
        this.order = order;
        return this;
    }

    public ByteBufferList() {
    }

    public ByteBufferList(ByteBuffer... b) {
        addAll(b);
    }

    public ByteBufferList(byte[] buf) {
        ByteBuffer b = ByteBuffer.wrap(buf);
        add(b);
    }

    public ByteBufferList addAll(ByteBuffer... bb) {
        for (ByteBuffer b : bb) {
            add(b);
        }
        return this;
    }

    public ByteBufferList addAll(ByteBufferList... bb) {
        for (ByteBufferList b : bb) {
            b.get(this);
        }
        return this;
    }

    public byte[] getBytes(int length) {
        byte[] ret = new byte[length];
        get(ret);
        return ret;
    }

    public byte[] getAllByteArray() {
        if (this.mBuffers.size() == 1) {
            ByteBuffer peek = this.mBuffers.peek();
            if (peek.capacity() == remaining() && peek.isDirect()) {
                this.remaining = 0;
                return this.mBuffers.remove().array();
            }
        }
        byte[] ret = new byte[remaining()];
        get(ret);
        return ret;
    }

    public ByteBuffer[] getAllArray() {
        ByteBuffer[] ret = new ByteBuffer[this.mBuffers.size()];
        ByteBuffer[] ret2 = (ByteBuffer[]) this.mBuffers.toArray(ret);
        this.mBuffers.clear();
        this.remaining = 0;
        return ret2;
    }

    public boolean isEmpty() {
        return this.remaining == 0;
    }

    public int remaining() {
        return this.remaining;
    }

    public boolean hasRemaining() {
        return remaining() > 0;
    }

    public short peekShort() {
        return read(2).duplicate().getShort();
    }

    public int peekInt() {
        return read(4).duplicate().getInt();
    }

    public long peekLong() {
        return read(8).duplicate().getLong();
    }

    public byte[] peekBytes(int size) {
        byte[] ret = new byte[size];
        read(size).duplicate().get(ret);
        return ret;
    }

    public ByteBufferList skip(int length) {
        get(null, 0, length);
        return this;
    }

    public int getInt() {
        int ret = read(4).getInt();
        this.remaining -= 4;
        return ret;
    }

    public char getByteChar() {
        char ret = (char) read(1).get();
        this.remaining--;
        return ret;
    }

    public short getShort() {
        short ret = read(2).getShort();
        this.remaining -= 2;
        return ret;
    }

    public byte get() {
        byte ret = read(1).get();
        this.remaining--;
        return ret;
    }

    public long getLong() {
        long ret = read(8).getLong();
        this.remaining -= 8;
        return ret;
    }

    public void get(byte[] bytes) {
        get(bytes, 0, bytes.length);
    }

    public void get(byte[] bytes, int offset, int length) {
        if (remaining() < length) {
            throw new IllegalArgumentException("length");
        }
        int need = length;
        while (need > 0) {
            ByteBuffer b = this.mBuffers.peek();
            int read = Math.min(b.remaining(), need);
            if (bytes != null) {
                b.get(bytes, offset, read);
            } else {
                b.position(b.position() + read);
            }
            need -= read;
            offset += read;
            if (b.remaining() == 0) {
                ByteBuffer removed = this.mBuffers.remove();
                if (!$assertionsDisabled && b != removed) {
                    throw new AssertionError();
                }
                reclaim(b);
            }
        }
        this.remaining -= length;
    }

    public void get(ByteBufferList into, int length) {
        if (remaining() < length) {
            throw new IllegalArgumentException("length");
        }
        int offset = 0;
        while (true) {
            if (offset >= length) {
                break;
            }
            ByteBuffer b = this.mBuffers.remove();
            int remaining = b.remaining();
            if (remaining == 0) {
                reclaim(b);
            } else if (offset + remaining > length) {
                int need = length - offset;
                ByteBuffer subset = obtain(need);
                subset.limit(need);
                b.get(subset.array(), 0, need);
                into.add(subset);
                this.mBuffers.addFirst(b);
                if (!$assertionsDisabled && subset.capacity() < need) {
                    throw new AssertionError();
                }
                if (!$assertionsDisabled && subset.position() != 0) {
                    throw new AssertionError();
                }
            } else {
                into.add(b);
                offset += remaining;
            }
        }
        this.remaining -= length;
    }

    public void get(ByteBufferList into) {
        get(into, remaining());
    }

    public ByteBufferList get(int length) {
        ByteBufferList ret = new ByteBufferList();
        get(ret, length);
        return ret.order(this.order);
    }

    public ByteBuffer getAll() {
        if (remaining() == 0) {
            return EMPTY_BYTEBUFFER;
        }
        read(remaining());
        return remove();
    }

    private ByteBuffer read(int count) {
        if (remaining() < count) {
            throw new IllegalArgumentException("count : " + remaining() + "/" + count);
        }
        ByteBuffer first = this.mBuffers.peek();
        while (first != null && !first.hasRemaining()) {
            reclaim(this.mBuffers.remove());
            ByteBuffer first2 = this.mBuffers.peek();
            first = first2;
        }
        if (first == null) {
            return EMPTY_BYTEBUFFER;
        }
        if (first.remaining() >= count) {
            return first.order(this.order);
        }
        ByteBuffer ret = obtain(count);
        ret.limit(count);
        byte[] bytes = ret.array();
        int offset = 0;
        ByteBuffer bb = null;
        while (offset < count) {
            ByteBuffer bb2 = this.mBuffers.remove();
            bb = bb2;
            int toRead = Math.min(count - offset, bb.remaining());
            bb.get(bytes, offset, toRead);
            offset += toRead;
            if (bb.remaining() == 0) {
                reclaim(bb);
                bb = null;
            }
        }
        if (bb != null && bb.remaining() > 0) {
            this.mBuffers.addFirst(bb);
        }
        this.mBuffers.addFirst(ret);
        return ret.order(this.order);
    }

    public void trim() {
        read(0);
    }

    public ByteBufferList add(ByteBufferList b) {
        b.get(this);
        return this;
    }

    /* JADX WARN: Removed duplicated region for block: B:11:0x0055  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public com.koushikdutta.async.ByteBufferList add(java.nio.ByteBuffer r4) {
        /*
            r3 = this;
            int r1 = r4.remaining()
            if (r1 > 0) goto La
            reclaim(r4)
        L9:
            return r3
        La:
            int r1 = r4.remaining()
            r3.addRemaining(r1)
            com.koushikdutta.async.ArrayDeque<java.nio.ByteBuffer> r1 = r3.mBuffers
            int r1 = r1.size()
            if (r1 <= 0) goto L55
            com.koushikdutta.async.ArrayDeque<java.nio.ByteBuffer> r1 = r3.mBuffers
            java.lang.Object r0 = r1.getLast()
            java.nio.ByteBuffer r0 = (java.nio.ByteBuffer) r0
            int r1 = r0.capacity()
            int r2 = r0.limit()
            int r1 = r1 - r2
            int r2 = r4.remaining()
            if (r1 < r2) goto L55
            r0.mark()
            int r1 = r0.limit()
            r0.position(r1)
            int r1 = r0.capacity()
            r0.limit(r1)
            r0.put(r4)
            int r1 = r0.position()
            r0.limit(r1)
            r0.reset()
            reclaim(r4)
            r3.trim()
            goto L9
        L55:
            com.koushikdutta.async.ArrayDeque<java.nio.ByteBuffer> r1 = r3.mBuffers
            r1.add(r4)
            r3.trim()
            goto L9
        */
        throw new UnsupportedOperationException("Method not decompiled: com.koushikdutta.async.ByteBufferList.add(java.nio.ByteBuffer):com.koushikdutta.async.ByteBufferList");
    }

    public void addFirst(ByteBuffer b) {
        if (b.remaining() <= 0) {
            reclaim(b);
            return;
        }
        addRemaining(b.remaining());
        if (this.mBuffers.size() > 0) {
            ByteBuffer first = this.mBuffers.getFirst();
            if (first.position() >= b.remaining()) {
                first.position(first.position() - b.remaining());
                first.mark();
                first.put(b);
                first.reset();
                reclaim(b);
                return;
            }
        }
        this.mBuffers.addFirst(b);
    }

    private void addRemaining(int remaining) {
        if (remaining() >= 0) {
            this.remaining += remaining;
        }
    }

    public void recycle() {
        while (this.mBuffers.size() > 0) {
            reclaim(this.mBuffers.remove());
        }
        if (!$assertionsDisabled && this.mBuffers.size() != 0) {
            throw new AssertionError();
        }
        this.remaining = 0;
    }

    public ByteBuffer remove() {
        ByteBuffer ret = this.mBuffers.remove();
        this.remaining -= ret.remaining();
        return ret;
    }

    public int size() {
        return this.mBuffers.size();
    }

    public void spewString() {
        System.out.println(peekString());
    }

    public String peekString() {
        return peekString(null);
    }

    public String peekString(Charset charset) {
        byte[] bytes;
        int offset;
        int length;
        if (charset == null) {
            charset = Charsets.US_ASCII;
        }
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer bb : this.mBuffers) {
            if (bb.isDirect()) {
                bytes = new byte[bb.remaining()];
                offset = 0;
                length = bb.remaining();
                bb.get(bytes);
            } else {
                bytes = bb.array();
                offset = bb.arrayOffset() + bb.position();
                length = bb.remaining();
            }
            builder.append(new String(bytes, offset, length, charset));
        }
        return builder.toString();
    }

    public String readString() {
        return readString(null);
    }

    public String readString(Charset charset) {
        String ret = peekString(charset);
        recycle();
        return ret;
    }

    static class Reclaimer implements Comparator<ByteBuffer> {
        Reclaimer() {
        }

        @Override // java.util.Comparator
        public int compare(ByteBuffer byteBuffer, ByteBuffer byteBuffer2) {
            if (byteBuffer.capacity() == byteBuffer2.capacity()) {
                return 0;
            }
            if (byteBuffer.capacity() > byteBuffer2.capacity()) {
                return 1;
            }
            return -1;
        }
    }

    private static PriorityQueue<ByteBuffer> getReclaimed() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null || Thread.currentThread() != mainLooper.getThread()) {
            return reclaimed;
        }
        return null;
    }

    public static void setMaxPoolSize(int size) {
        MAX_SIZE = size;
    }

    public static void setMaxItemSize(int size) {
        MAX_ITEM_SIZE = size;
    }

    private static boolean reclaimedContains(ByteBuffer b) {
        for (ByteBuffer other : reclaimed) {
            if (other == b) {
                return true;
            }
        }
        return false;
    }

    public static void reclaim(ByteBuffer b) {
        PriorityQueue<ByteBuffer> r;
        if (b != null && !b.isDirect() && b.arrayOffset() == 0 && b.array().length == b.capacity() && b.capacity() >= 8192 && b.capacity() <= MAX_ITEM_SIZE && (r = getReclaimed()) != null) {
            synchronized (LOCK) {
                while (currentSize > MAX_SIZE && r.size() > 0 && r.peek().capacity() < b.capacity()) {
                    ByteBuffer head = r.remove();
                    currentSize -= head.capacity();
                }
                if (currentSize <= MAX_SIZE) {
                    if (!$assertionsDisabled && reclaimedContains(b)) {
                        throw new AssertionError();
                    }
                    b.position(0);
                    b.limit(b.capacity());
                    currentSize += b.capacity();
                    r.add(b);
                    if (!$assertionsDisabled) {
                        if (!((currentSize == 0) ^ (r.size() != 0))) {
                            throw new AssertionError();
                        }
                    }
                    maxItem = Math.max(maxItem, b.capacity());
                }
            }
        }
    }

    public static ByteBuffer obtain(int size) {
        PriorityQueue<ByteBuffer> r;
        if (size <= maxItem && (r = getReclaimed()) != null) {
            synchronized (LOCK) {
                while (r.size() > 0) {
                    ByteBuffer ret = r.remove();
                    if (r.size() == 0) {
                        maxItem = 0;
                    }
                    currentSize -= ret.capacity();
                    if (!$assertionsDisabled) {
                        if (!((currentSize == 0) ^ (r.size() != 0))) {
                            throw new AssertionError();
                        }
                    }
                    if (ret.capacity() >= size) {
                        return ret;
                    }
                }
            }
        }
        return ByteBuffer.allocate(Math.max(8192, size));
    }

    public static void obtainArray(ByteBuffer[] arr, int size) {
        int index;
        int index2;
        PriorityQueue<ByteBuffer> r = getReclaimed();
        int index3 = 0;
        int total = 0;
        if (r == null) {
            index = 0;
        } else {
            synchronized (LOCK) {
                while (true) {
                    index = index3;
                    if (r.size() <= 0 || total >= size || index >= arr.length - 1) {
                        break;
                    }
                    ByteBuffer b = r.remove();
                    currentSize -= b.capacity();
                    if (!$assertionsDisabled) {
                        if (!((currentSize == 0) ^ (r.size() != 0))) {
                            throw new AssertionError();
                        }
                    }
                    int needed = Math.min(size - total, b.capacity());
                    total += needed;
                    index3 = index + 1;
                    try {
                        arr[index] = b;
                    }
                }
            }
        }
        if (total < size) {
            index2 = index + 1;
            arr[index] = ByteBuffer.allocate(Math.max(8192, size - total));
        } else {
            index2 = index;
        }
        for (int i = index2; i < arr.length; i++) {
            arr[i] = EMPTY_BYTEBUFFER;
        }
    }

    public static void writeOutputStream(OutputStream out, ByteBuffer b) throws IOException {
        byte[] bytes;
        int offset;
        int length;
        if (b.isDirect()) {
            bytes = new byte[b.remaining()];
            offset = 0;
            length = b.remaining();
            b.get(bytes);
        } else {
            bytes = b.array();
            offset = b.arrayOffset() + b.position();
            length = b.remaining();
        }
        out.write(bytes, offset, length);
    }
}
