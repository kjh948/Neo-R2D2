package com.koushikdutta.async;

import android.util.Log;
import com.koushikdutta.async.callback.DataCallback;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;

/* JADX INFO: loaded from: classes.dex */
public class PushParser implements DataCallback {
    static Hashtable<Class, Method> mTable = new Hashtable<>();
    DataEmitter mEmitter;
    private Waiter noopArgWaiter = new Waiter(0) { // from class: com.koushikdutta.async.PushParser.1
        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            PushParser.this.args.add(null);
            return null;
        }
    };
    private Waiter byteArgWaiter = new Waiter(1) { // from class: com.koushikdutta.async.PushParser.2
        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            PushParser.this.args.add(Byte.valueOf(bb.get()));
            return null;
        }
    };
    private Waiter shortArgWaiter = new Waiter(2) { // from class: com.koushikdutta.async.PushParser.3
        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            PushParser.this.args.add(Short.valueOf(bb.getShort()));
            return null;
        }
    };
    private Waiter intArgWaiter = new Waiter(4) { // from class: com.koushikdutta.async.PushParser.4
        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            PushParser.this.args.add(Integer.valueOf(bb.getInt()));
            return null;
        }
    };
    private Waiter longArgWaiter = new Waiter(8) { // from class: com.koushikdutta.async.PushParser.5
        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            PushParser.this.args.add(Long.valueOf(bb.getLong()));
            return null;
        }
    };
    private ParseCallback<byte[]> byteArrayArgCallback = new ParseCallback<byte[]>() { // from class: com.koushikdutta.async.PushParser.6
        @Override // com.koushikdutta.async.PushParser.ParseCallback
        public void parsed(byte[] data) {
            PushParser.this.args.add(data);
        }
    };
    private ParseCallback<ByteBufferList> byteBufferListArgCallback = new ParseCallback<ByteBufferList>() { // from class: com.koushikdutta.async.PushParser.7
        @Override // com.koushikdutta.async.PushParser.ParseCallback
        public void parsed(ByteBufferList data) {
            PushParser.this.args.add(data);
        }
    };
    private ParseCallback<byte[]> stringArgCallback = new ParseCallback<byte[]>() { // from class: com.koushikdutta.async.PushParser.8
        @Override // com.koushikdutta.async.PushParser.ParseCallback
        public void parsed(byte[] data) {
            PushParser.this.args.add(new String(data));
        }
    };
    private LinkedList<Waiter> mWaiting = new LinkedList<>();
    private ArrayList<Object> args = new ArrayList<>();
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    ByteBufferList pending = new ByteBufferList();

    public interface ParseCallback<T> {
        void parsed(T t);
    }

    static abstract class Waiter {
        int length;

        public abstract Waiter onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList);

        public Waiter(int length) {
            this.length = length;
        }
    }

    static class IntWaiter extends Waiter {
        ParseCallback<Integer> callback;

        public IntWaiter(ParseCallback<Integer> callback) {
            super(4);
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            this.callback.parsed(Integer.valueOf(bb.getInt()));
            return null;
        }
    }

    static class ByteArrayWaiter extends Waiter {
        ParseCallback<byte[]> callback;

        public ByteArrayWaiter(int length, ParseCallback<byte[]> callback) {
            super(length);
            if (length <= 0) {
                throw new IllegalArgumentException("length should be > 0");
            }
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            byte[] bytes = new byte[this.length];
            bb.get(bytes);
            this.callback.parsed(bytes);
            return null;
        }
    }

    static class LenByteArrayWaiter extends Waiter {
        private final ParseCallback<byte[]> callback;

        public LenByteArrayWaiter(ParseCallback<byte[]> callback) {
            super(4);
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            int length = bb.getInt();
            if (length != 0) {
                return new ByteArrayWaiter(length, this.callback);
            }
            this.callback.parsed(new byte[0]);
            return null;
        }
    }

    static class ByteBufferListWaiter extends Waiter {
        ParseCallback<ByteBufferList> callback;

        public ByteBufferListWaiter(int length, ParseCallback<ByteBufferList> callback) {
            super(length);
            if (length <= 0) {
                throw new IllegalArgumentException("length should be > 0");
            }
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            this.callback.parsed(bb.get(this.length));
            return null;
        }
    }

    static class LenByteBufferListWaiter extends Waiter {
        private final ParseCallback<ByteBufferList> callback;

        public LenByteBufferListWaiter(ParseCallback<ByteBufferList> callback) {
            super(4);
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            int length = bb.getInt();
            return new ByteBufferListWaiter(length, this.callback);
        }
    }

    static class UntilWaiter extends Waiter {
        DataCallback callback;
        byte value;

        public UntilWaiter(byte value, DataCallback callback) {
            super(1);
            this.value = value;
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            boolean found = true;
            ByteBufferList cb = new ByteBufferList();
            while (true) {
                if (bb.size() <= 0) {
                    break;
                }
                ByteBuffer b = bb.remove();
                b.mark();
                int index = 0;
                while (b.remaining() > 0) {
                    found = b.get() == this.value;
                    if (found) {
                        break;
                    }
                    index++;
                }
                b.reset();
                if (found) {
                    bb.addFirst(b);
                    bb.get(cb, index);
                    bb.get();
                    break;
                }
                cb.add(b);
            }
            this.callback.onDataAvailable(emitter, cb);
            if (found) {
                return null;
            }
            return this;
        }
    }

    private class TapWaiter extends Waiter {
        private final TapCallback callback;

        public TapWaiter(TapCallback callback) {
            super(0);
            this.callback = callback;
        }

        @Override // com.koushikdutta.async.PushParser.Waiter
        public Waiter onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            Method method = PushParser.getTap(this.callback);
            method.setAccessible(true);
            try {
                method.invoke(this.callback, PushParser.this.args.toArray());
            } catch (Exception e) {
                Log.e("PushParser", "Error while invoking tap callback", e);
            }
            PushParser.this.args.clear();
            return null;
        }
    }

    public PushParser setOrder(ByteOrder order) {
        this.order = order;
        return this;
    }

    public PushParser(DataEmitter s) {
        this.mEmitter = s;
        this.mEmitter.setDataCallback(this);
    }

    public PushParser readInt(ParseCallback<Integer> callback) {
        this.mWaiting.add(new IntWaiter(callback));
        return this;
    }

    public PushParser readByteArray(int length, ParseCallback<byte[]> callback) {
        this.mWaiting.add(new ByteArrayWaiter(length, callback));
        return this;
    }

    public PushParser readByteBufferList(int length, ParseCallback<ByteBufferList> callback) {
        this.mWaiting.add(new ByteBufferListWaiter(length, callback));
        return this;
    }

    public PushParser until(byte b, DataCallback callback) {
        this.mWaiting.add(new UntilWaiter(b, callback));
        return this;
    }

    public PushParser readByte() {
        this.mWaiting.add(this.byteArgWaiter);
        return this;
    }

    public PushParser readShort() {
        this.mWaiting.add(this.shortArgWaiter);
        return this;
    }

    public PushParser readInt() {
        this.mWaiting.add(this.intArgWaiter);
        return this;
    }

    public PushParser readLong() {
        this.mWaiting.add(this.longArgWaiter);
        return this;
    }

    public PushParser readByteArray(int length) {
        return length == -1 ? readLenByteArray() : readByteArray(length, this.byteArrayArgCallback);
    }

    public PushParser readLenByteArray() {
        this.mWaiting.add(new LenByteArrayWaiter(this.byteArrayArgCallback));
        return this;
    }

    public PushParser readByteBufferList(int length) {
        return length == -1 ? readLenByteBufferList() : readByteBufferList(length, this.byteBufferListArgCallback);
    }

    public PushParser readLenByteBufferList() {
        return readLenByteBufferList(this.byteBufferListArgCallback);
    }

    public PushParser readLenByteBufferList(ParseCallback<ByteBufferList> callback) {
        this.mWaiting.add(new LenByteBufferListWaiter(callback));
        return this;
    }

    public PushParser readString() {
        this.mWaiting.add(new LenByteArrayWaiter(this.stringArgCallback));
        return this;
    }

    public PushParser noop() {
        this.mWaiting.add(this.noopArgWaiter);
        return this;
    }

    @Override // com.koushikdutta.async.callback.DataCallback
    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
        bb.get(this.pending);
        while (this.mWaiting.size() > 0 && this.pending.remaining() >= this.mWaiting.peek().length) {
            this.pending.order(this.order);
            Waiter next = this.mWaiting.poll().onDataAvailable(emitter, this.pending);
            if (next != null) {
                this.mWaiting.addFirst(next);
            }
        }
        if (this.mWaiting.size() == 0) {
            this.pending.get(bb);
        }
    }

    public void tap(TapCallback callback) {
        this.mWaiting.add(new TapWaiter(callback));
    }

    static Method getTap(TapCallback callback) {
        Method found = mTable.get(callback.getClass());
        if (found == null) {
            for (Method method : callback.getClass().getMethods()) {
                if ("tap".equals(method.getName())) {
                    mTable.put(callback.getClass(), method);
                    return method;
                }
            }
            Method[] candidates = callback.getClass().getDeclaredMethods();
            if (candidates.length == 1) {
                return candidates[0];
            }
            throw new AssertionError("-keep class * extends com.koushikdutta.async.TapCallback {\n    *;\n}\n");
        }
        return found;
    }
}
