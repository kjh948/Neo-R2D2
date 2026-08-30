package com.koushikdutta.async;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/* JADX INFO: loaded from: classes.dex */
public class ArrayDeque<E> extends AbstractCollection<E> implements Deque<E>, Cloneable, Serializable {
    static final /* synthetic */ boolean $assertionsDisabled;
    private static final int MIN_INITIAL_CAPACITY = 8;
    private static final long serialVersionUID = 2340985798034038923L;
    private transient Object[] elements;
    private transient int head;
    private transient int tail;

    static {
        $assertionsDisabled = !ArrayDeque.class.desiredAssertionStatus();
    }

    private void allocateElements(int numElements) {
        int initialCapacity = 8;
        if (numElements >= 8) {
            int initialCapacity2 = numElements | (numElements >>> 1);
            int initialCapacity3 = initialCapacity2 | (initialCapacity2 >>> 2);
            int initialCapacity4 = initialCapacity3 | (initialCapacity3 >>> 4);
            int initialCapacity5 = initialCapacity4 | (initialCapacity4 >>> 8);
            initialCapacity = (initialCapacity5 | (initialCapacity5 >>> 16)) + 1;
            if (initialCapacity < 0) {
                initialCapacity >>>= 1;
            }
        }
        this.elements = new Object[initialCapacity];
    }

    private void doubleCapacity() {
        if (!$assertionsDisabled && this.head != this.tail) {
            throw new AssertionError();
        }
        int p = this.head;
        int n = this.elements.length;
        int r = n - p;
        int newCapacity = n << 1;
        if (newCapacity < 0) {
            throw new IllegalStateException("Sorry, deque too big");
        }
        Object[] a = new Object[newCapacity];
        System.arraycopy(this.elements, p, a, 0, r);
        System.arraycopy(this.elements, 0, a, r, p);
        this.elements = a;
        this.head = 0;
        this.tail = n;
    }

    private <T> T[] copyElements(T[] a) {
        if (this.head < this.tail) {
            System.arraycopy(this.elements, this.head, a, 0, size());
        } else if (this.head > this.tail) {
            int headPortionLen = this.elements.length - this.head;
            System.arraycopy(this.elements, this.head, a, 0, headPortionLen);
            System.arraycopy(this.elements, 0, a, headPortionLen, this.tail);
        }
        return a;
    }

    public ArrayDeque() {
        this.elements = new Object[16];
    }

    public ArrayDeque(int numElements) {
        allocateElements(numElements);
    }

    public ArrayDeque(Collection<? extends E> c) {
        allocateElements(c.size());
        addAll(c);
    }

    @Override // com.koushikdutta.async.Deque
    public void addFirst(E e) {
        if (e == null) {
            throw new NullPointerException("e == null");
        }
        Object[] objArr = this.elements;
        int length = (this.head - 1) & (this.elements.length - 1);
        this.head = length;
        objArr[length] = e;
        if (this.head == this.tail) {
            doubleCapacity();
        }
    }

    @Override // com.koushikdutta.async.Deque
    public void addLast(E e) {
        if (e == null) {
            throw new NullPointerException("e == null");
        }
        this.elements[this.tail] = e;
        int length = (this.tail + 1) & (this.elements.length - 1);
        this.tail = length;
        if (length == this.head) {
            doubleCapacity();
        }
    }

    @Override // com.koushikdutta.async.Deque
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    @Override // com.koushikdutta.async.Deque
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    @Override // com.koushikdutta.async.Deque
    public E removeFirst() {
        E x = pollFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override // com.koushikdutta.async.Deque
    public E removeLast() {
        E x = pollLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @Override // com.koushikdutta.async.Deque
    public E pollFirst() {
        int i = this.head;
        E e = (E) this.elements[i];
        if (e == null) {
            return null;
        }
        this.elements[i] = null;
        this.head = (i + 1) & (this.elements.length - 1);
        return e;
    }

    @Override // com.koushikdutta.async.Deque
    public E pollLast() {
        int length = (this.tail - 1) & (this.elements.length - 1);
        E e = (E) this.elements[length];
        if (e == null) {
            return null;
        }
        this.elements[length] = null;
        this.tail = length;
        return e;
    }

    @Override // com.koushikdutta.async.Deque
    public E getFirst() {
        E e = (E) this.elements[this.head];
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e;
    }

    @Override // com.koushikdutta.async.Deque
    public E getLast() {
        E e = (E) this.elements[(this.tail - 1) & (this.elements.length - 1)];
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e;
    }

    @Override // com.koushikdutta.async.Deque
    public E peekFirst() {
        return (E) this.elements[this.head];
    }

    @Override // com.koushikdutta.async.Deque
    public E peekLast() {
        return (E) this.elements[(this.tail - 1) & (this.elements.length - 1)];
    }

    @Override // com.koushikdutta.async.Deque
    public boolean removeFirstOccurrence(Object o) {
        if (o == null) {
            return false;
        }
        int mask = this.elements.length - 1;
        int i = this.head;
        while (true) {
            Object x = this.elements[i];
            if (x == null) {
                return false;
            }
            if (o.equals(x)) {
                delete(i);
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    @Override // com.koushikdutta.async.Deque
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            return false;
        }
        int mask = this.elements.length - 1;
        int i = this.tail - 1;
        while (true) {
            int i2 = i & mask;
            Object x = this.elements[i2];
            if (x == null) {
                return false;
            }
            if (o.equals(x)) {
                delete(i2);
                return true;
            }
            i = i2 - 1;
        }
    }

    @Override // java.util.AbstractCollection, java.util.Collection, com.koushikdutta.async.Deque, java.util.Queue
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    @Override // com.koushikdutta.async.Deque, java.util.Queue
    public boolean offer(E e) {
        return offerLast(e);
    }

    @Override // com.koushikdutta.async.Deque, java.util.Queue
    public E remove() {
        return removeFirst();
    }

    @Override // com.koushikdutta.async.Deque, java.util.Queue
    public E poll() {
        return pollFirst();
    }

    @Override // com.koushikdutta.async.Deque, java.util.Queue
    public E element() {
        return getFirst();
    }

    @Override // com.koushikdutta.async.Deque, java.util.Queue
    public E peek() {
        return peekFirst();
    }

    @Override // com.koushikdutta.async.Deque
    public void push(E e) {
        addFirst(e);
    }

    @Override // com.koushikdutta.async.Deque
    public E pop() {
        return removeFirst();
    }

    private void checkInvariants() {
        if (!$assertionsDisabled && this.elements[this.tail] != null) {
            throw new AssertionError();
        }
        if (!$assertionsDisabled && (this.head != this.tail ? this.elements[this.head] == null || this.elements[(this.tail - 1) & (this.elements.length - 1)] == null : this.elements[this.head] != null)) {
            throw new AssertionError();
        }
        if (!$assertionsDisabled && this.elements[(this.head - 1) & (this.elements.length - 1)] != null) {
            throw new AssertionError();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean delete(int i) {
        checkInvariants();
        Object[] elements = this.elements;
        int mask = elements.length - 1;
        int h = this.head;
        int t = this.tail;
        int front = (i - h) & mask;
        int back = (t - i) & mask;
        if (front >= ((t - h) & mask)) {
            throw new ConcurrentModificationException();
        }
        if (front < back) {
            if (h <= i) {
                System.arraycopy(elements, h, elements, h + 1, front);
            } else {
                System.arraycopy(elements, 0, elements, 1, i);
                elements[0] = elements[mask];
                System.arraycopy(elements, h, elements, h + 1, mask - h);
            }
            elements[h] = null;
            this.head = (h + 1) & mask;
            return false;
        }
        if (i < t) {
            System.arraycopy(elements, i + 1, elements, i, back);
            this.tail = t - 1;
        } else {
            System.arraycopy(elements, i + 1, elements, i, mask - i);
            elements[mask] = elements[0];
            System.arraycopy(elements, 1, elements, 0, t);
            this.tail = (t - 1) & mask;
        }
        return true;
    }

    @Override // java.util.AbstractCollection, java.util.Collection, com.koushikdutta.async.Deque
    public int size() {
        return (this.tail - this.head) & (this.elements.length - 1);
    }

    @Override // java.util.AbstractCollection, java.util.Collection
    public boolean isEmpty() {
        return this.head == this.tail;
    }

    @Override // java.util.AbstractCollection, java.util.Collection, java.lang.Iterable, com.koushikdutta.async.Deque
    public Iterator<E> iterator() {
        return new DeqIterator();
    }

    @Override // com.koushikdutta.async.Deque
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    private class DeqIterator implements Iterator<E> {
        private int cursor;
        private int fence;
        private int lastRet;

        private DeqIterator() {
            this.cursor = ArrayDeque.this.head;
            this.fence = ArrayDeque.this.tail;
            this.lastRet = -1;
        }

        @Override // java.util.Iterator
        public boolean hasNext() {
            return this.cursor != this.fence;
        }

        @Override // java.util.Iterator
        public E next() {
            if (this.cursor != this.fence) {
                E e = (E) ArrayDeque.this.elements[this.cursor];
                if (ArrayDeque.this.tail != this.fence || e == null) {
                    throw new ConcurrentModificationException();
                }
                this.lastRet = this.cursor;
                this.cursor = (this.cursor + 1) & (ArrayDeque.this.elements.length - 1);
                return e;
            }
            throw new NoSuchElementException();
        }

        @Override // java.util.Iterator
        public void remove() {
            if (this.lastRet >= 0) {
                if (ArrayDeque.this.delete(this.lastRet)) {
                    this.cursor = (this.cursor - 1) & (ArrayDeque.this.elements.length - 1);
                    this.fence = ArrayDeque.this.tail;
                }
                this.lastRet = -1;
                return;
            }
            throw new IllegalStateException();
        }
    }

    private class DescendingIterator implements Iterator<E> {
        private int cursor;
        private int fence;
        private int lastRet;

        private DescendingIterator() {
            this.cursor = ArrayDeque.this.tail;
            this.fence = ArrayDeque.this.head;
            this.lastRet = -1;
        }

        @Override // java.util.Iterator
        public boolean hasNext() {
            return this.cursor != this.fence;
        }

        @Override // java.util.Iterator
        public E next() {
            if (this.cursor != this.fence) {
                this.cursor = (this.cursor - 1) & (ArrayDeque.this.elements.length - 1);
                E e = (E) ArrayDeque.this.elements[this.cursor];
                if (ArrayDeque.this.head != this.fence || e == null) {
                    throw new ConcurrentModificationException();
                }
                this.lastRet = this.cursor;
                return e;
            }
            throw new NoSuchElementException();
        }

        @Override // java.util.Iterator
        public void remove() {
            if (this.lastRet >= 0) {
                if (!ArrayDeque.this.delete(this.lastRet)) {
                    this.cursor = (this.cursor + 1) & (ArrayDeque.this.elements.length - 1);
                    this.fence = ArrayDeque.this.head;
                }
                this.lastRet = -1;
                return;
            }
            throw new IllegalStateException();
        }
    }

    @Override // java.util.AbstractCollection, java.util.Collection, com.koushikdutta.async.Deque
    public boolean contains(Object o) {
        if (o == null) {
            return false;
        }
        int mask = this.elements.length - 1;
        int i = this.head;
        while (true) {
            Object x = this.elements[i];
            if (x == null) {
                return false;
            }
            if (o.equals(x)) {
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    @Override // java.util.AbstractCollection, java.util.Collection, com.koushikdutta.async.Deque
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    @Override // java.util.AbstractCollection, java.util.Collection
    public void clear() {
        int h = this.head;
        int t = this.tail;
        if (h != t) {
            this.tail = 0;
            this.head = 0;
            int i = h;
            int mask = this.elements.length - 1;
            do {
                this.elements[i] = null;
                i = (i + 1) & mask;
            } while (i != t);
        }
    }

    @Override // java.util.AbstractCollection, java.util.Collection
    public Object[] toArray() {
        return copyElements(new Object[size()]);
    }

    @Override // java.util.AbstractCollection, java.util.Collection
    public <T> T[] toArray(T[] tArr) {
        int size = size();
        if (tArr.length < size) {
            tArr = (T[]) ((Object[]) Array.newInstance(tArr.getClass().getComponentType(), size));
        }
        copyElements(tArr);
        if (tArr.length > size) {
            tArr[size] = null;
        }
        return tArr;
    }

    /* JADX INFO: renamed from: clone, reason: merged with bridge method [inline-methods] */
    public ArrayDeque<E> m7clone() {
        try {
            ArrayDeque<E> result = (ArrayDeque) super.clone();
            System.arraycopy(this.elements, 0, result.elements, 0, this.elements.length);
            return result;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        s.writeInt(size());
        int mask = this.elements.length - 1;
        for (int i = this.head; i != this.tail; i = (i + 1) & mask) {
            s.writeObject(this.elements[i]);
        }
    }

    private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
        s.defaultReadObject();
        int size = s.readInt();
        allocateElements(size);
        this.head = 0;
        this.tail = size;
        for (int i = 0; i < size; i++) {
            this.elements[i] = s.readObject();
        }
    }
}
