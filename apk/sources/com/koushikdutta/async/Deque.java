package com.koushikdutta.async;

import java.util.Iterator;
import java.util.Queue;

/* JADX INFO: loaded from: classes.dex */
public interface Deque<E> extends Queue<E> {
    @Override // java.util.Collection, com.koushikdutta.async.Deque, java.util.Queue
    boolean add(E e);

    void addFirst(E e);

    void addLast(E e);

    boolean contains(Object obj);

    Iterator<E> descendingIterator();

    @Override // java.util.Queue
    E element();

    E getFirst();

    E getLast();

    Iterator<E> iterator();

    @Override // java.util.Queue
    boolean offer(E e);

    boolean offerFirst(E e);

    boolean offerLast(E e);

    @Override // java.util.Queue
    E peek();

    E peekFirst();

    E peekLast();

    @Override // java.util.Queue
    E poll();

    E pollFirst();

    E pollLast();

    E pop();

    void push(E e);

    @Override // java.util.Queue
    E remove();

    boolean remove(Object obj);

    E removeFirst();

    boolean removeFirstOccurrence(Object obj);

    E removeLast();

    boolean removeLastOccurrence(Object obj);

    int size();
}
