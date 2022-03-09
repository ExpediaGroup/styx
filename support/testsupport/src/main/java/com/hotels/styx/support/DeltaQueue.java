package com.hotels.styx.support;

//Copyright (c) 2000-2017, jMock.org
//        All rights reserved.
//
//        Redistribution and use in source and binary forms, with or without
//        modification, are permitted provided that the following conditions are
//        met:
//
//        Redistributions of source code must retain the above copyright notice,
//        this list of conditions and the following disclaimer. Redistributions
//        in binary form must reproduce the above copyright notice, this list of
//        conditions and the following disclaimer in the documentation and/or
//        other materials provided with the distribution.
//
//        Neither the name of jMock nor the names of its contributors may be
//        used to endorse or promote products derived from this software without
//        specific prior written permission.
//
//        THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
//        "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
//        LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
//        A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
//        OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//        SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
//        LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
//        DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
//        THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//        (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
//        OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

/*
 * Code borrowed from jMock - http://jmock.org/ library.
 *
 * Reference:
 * https://github.com/jmock-developers/jmock-library/blob/master/jmock/src/main/java/org/jmock/lib/concurrent/internal/DeltaQueue.java
 *
 */

class DeltaQueue<T> {
    private static class Node<T> {
        public final T value;
        public long delay;
        public Node<T> next = null;

        public Node(T value, long nanos) {
            this.value = value;
            this.delay = nanos;
        }
    }

    private Node<T> head = null;

    public boolean isEmpty() {
        return head == null;
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public T next() {
        return head.value;
    }

    public long delay() {
        return head.delay;
    }

    public void add(long delay, T value) {
        Node<T> newNode = new Node<>(value, delay);

        Node<T> prev = null;
        Node<T> next = head;

        while (next != null && next.delay <= newNode.delay) {
            newNode.delay -= next.delay;
            prev = next;
            next = next.next;
        }

        if (prev == null) {
            head = newNode;
        } else {
            prev.next = newNode;
        }

        if (next != null) {
            next.delay -= newNode.delay;

            newNode.next = next;
        }
    }

    public long tick(long timeUnits) {
        if (head == null) {
            return 0L;
        } else if (head.delay >= timeUnits) {
            head.delay -= timeUnits;
            return 0L;
        } else {
            long leftover = timeUnits - head.delay;
            head.delay = 0L;
            return leftover;
        }
    }

    public T pop() {
        if (head.delay > 0) {
            throw new IllegalStateException("cannot pop the head element when it has a non-zero delay");
        }

        T popped = head.value;
        head = head.next;
        return popped;
    }

    public boolean remove(T element) {
        Node<T> prev = null;
        Node<T> node = head;
        while (node != null && node.value != element) {
            prev = node;
            node = node.next;
        }

        if (node == null) {
            return false;
        }

        if (node.next != null) {
            node.next.delay += node.delay;
        }

        if (prev == null) {
            head = node.next;
        } else {
            prev.next = node.next;
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName())
                .append("[");

        Node<T> node = head;
        while (node != null) {
            if (node != head) {
                sb.append(", ");
            }
            sb.append("+")
                    .append(node.delay)
                    .append(": ")
                    .append(node.value);

            node = node.next;
        }
        sb.append("]");

        return sb.toString();
    }
}