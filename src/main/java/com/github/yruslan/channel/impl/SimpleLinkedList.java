/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Ruslan Yushchenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * For more information, please refer to <http://opensource.org/licenses/MIT>
 */

package com.github.yruslan.channel.impl;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class SimpleLinkedList<T> {
    static class Elem<U> {
        public U el;
        public Elem<U> next;

        public Elem(U el, Elem<U> next) {
            this.el = el;
            this.next= next;
        }
    }

    private Elem<T> first = null;
    private Elem<T> last = null;
    private int count = 0;

    public boolean isEmpty() {
        return first == null;
    }

    public boolean nonEmpty() {
        return first != null;
    }

    public int size() {
        return count;
    }

    public T head() {
        if (first == null) {
            throw new NoSuchElementException();
        }
        return first.el;
    }

    synchronized public void clear() {
        first = null;
        last = null;
        count = 0;
    }

    synchronized public void append(T a) {
        Elem<T> newElement = new Elem<>(a, null);

        if (first == null) {
            first = newElement;
            last = first;
        } else {
            last.next = newElement;
            last = newElement;
        }
        count += 1;
    }

    synchronized public T returnHeadAndRotate() {
        if (first == null) {
            throw new NoSuchElementException();
        } else {
            T ret = first.el;
            rotate();
            return ret;
        }
    }

    synchronized public void remove(T a) {
        if (first == null) {
            return;
        }

        if (first.el == a) {
            dropFirst();
        } else {
            boolean removed = false;
            Elem<T> p = first;
            while (p.next != null && !removed) {
                if (p.next.el == a) {
                    p.next = p.next.next;
                    if (p.next == null) {
                        last = p;
                    }
                    removed = true;
                    count -= 1;
                } else {
                    p = p.next;
                }
            }
        }
    }

    synchronized public void foreach(Consumer<T> f) {
        Elem<T> p = first;
        while (p != null) {
            f.accept(p.el);
            p = p.next;
        }
    }

    private void dropFirst() {
        if (first == last) {
            clear();
        } else {
            first = first.next;
            count -= 1;
        }
    }

    private void rotate() {
        if (first != last) {
            Elem<T> tmp = first;
            first = tmp.next;
            last.next = tmp;
            last = tmp;
            tmp.next = null;
        }
    }
}
