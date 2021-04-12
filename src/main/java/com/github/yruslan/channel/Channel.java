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

package com.github.yruslan.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class Channel<T> implements ReadChannel<T>, WriteChannel<T> {
    static int NOT_AVAILABLE = 0;
    static int AVAILABLE = 1;
    static int CLOSED = 2;

    protected int readers = 0;
    protected int writers = 0;
    protected boolean closed = false;

    protected SimpleLinkedList<Semaphore> readWaiters = new SimpleLinkedList<>();
    protected SimpleLinkedList<Semaphore> writeWaiters = new SimpleLinkedList<>();

    // Java monitors are designed so each object can act as a mutex and a condition variable.
    // But this makes impossible to use a single lock for more than one condition.
    // So a lock from [java.util.concurrent.locks] is used instead. It allows to have several condition
    // variables that use a single lock.
    protected ReentrantLock lock = new ReentrantLock();
    protected Condition crd = lock.newCondition();
    protected Condition cwr = lock.newCondition();

    protected abstract boolean hasMessages();

    protected abstract boolean hasCapacity();

    protected abstract Optional<T> fetchValueOpt();

    @Override
    public final boolean ifEmptyAddReaderWaiter(Semaphore sem) {
        lock.lock();
        try {
            if (closed || hasMessages()) {
                return false;
            } else {
                readWaiters.append(sem);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public final boolean ifFullAddWriterWaiter(Semaphore sem) {
        lock.lock();
        try {
            if (closed || hasCapacity()) {
                return false;
            } else {
                writeWaiters.append(sem);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public final void fornew(Consumer<T> f) {
        Optional<T> valueOpt = tryRecv();
        valueOpt.ifPresent(f);
    }

    @Override
    public final void foreach(Consumer<T> f) throws InterruptedException {
        while (true) {
            lock.lock();
            readers += 1;
            while (!closed && !hasMessages()) {
                crd.await();
            }
            readers -= 1;
            if (isClosed()) {
                lock.unlock();
                return;
            }

            Optional<T> valOpt = fetchValueOpt();
            lock.unlock();

            valOpt.ifPresent(f);
        }
    }

    @Override
    public final Selector sender(T value, Runnable action) {
        return new Selector(true, this) {
            @Override
            boolean sendRecv() {
                return trySend(value);
            }

            @Override
            void afterAction() {
                action.run();
            }
        };
    }

    @Override
    public Selector recver(Consumer<T> action) {
        return new Selector(false, this) {
            T el = null;

            @Override
            boolean sendRecv() {
                Optional<T> optVal = tryRecv();
                optVal.ifPresent(t -> el = t);
                return optVal.isPresent();
            }

            @Override
            void afterAction() {
                action.accept(el);
            }
        };
    }

    @Override
    public int hasMessagesStatus() {
        lock.lock();
        int status;
        if (hasMessages()) {
            status = AVAILABLE;
        } else if (closed) {
            status = CLOSED;
        } else {
            status = NOT_AVAILABLE;
        }
        lock.unlock();
        return status;
    }

    @Override
    public int hasFreeCapacityStatus() {
        lock.lock();
        int status;
        if (hasCapacity()) {
            status = AVAILABLE;
        } else if (closed) {
            status = CLOSED;
        } else {
            status = NOT_AVAILABLE;
        }
        lock.unlock();
        return status;
    }

    @Override
    public void delReaderWaiter(Semaphore sem) {
        lock.lock();
        try {
            readWaiters.remove(sem);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void delWriterWaiter(Semaphore sem) {
        lock.lock();
        try {
            writeWaiters.remove(sem);
        } finally {
            lock.unlock();
        }
    }

    final protected void notifyReaders() {
        if (readers > 0) {
            crd.signal();
        } else {
            if (!readWaiters.isEmpty()) {
                readWaiters.returnHeadAndRotate().release();
            }
        }
    }

    final protected void notifyWriters() {
        if (writers > 0) {
            cwr.signal();
        } else {
            if (!writeWaiters.isEmpty()) {
                writeWaiters.returnHeadAndRotate().release();
            }
        }
    }

    /**
     * Create a synchronous channel.
     *
     * @param <T> The type of the channel.
     * @return A new channel
     */
    static <T> Channel<T> make() {
        return new SyncChannel<>();
    }

    /**
     * Create a channel. By default a synchronous channel will be created.
     * If bufferSize is greater then zero, a buffered channel will be created.
     *
     * @param bufferSize Asynchronous buffer size.
     * @param <T>        The type of the channel.
     * @return A new channel
     */
    static <T> Channel<T> make(int bufferSize) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("Buffer size cannot be negative.");
        }
        if (bufferSize > 0) {
            return new AsyncChannel<>(bufferSize);
        }
        return new SyncChannel<>();
    }

    /**
     * Waits for a non-blocking operation to be available on the list of channels.
     *
     * @param selector  A first channel to wait for (mandatory).
     * @param selectors Other channels to wait for.
     * @return true is none of the channels are closed and select() can be invoked again, false if at least one of channels is closed.
     */
    static boolean select(Selector selector, Selector... selectors) throws InterruptedException {
        return trySelect(Long.MAX_VALUE, selector, selectors);
    }

    /**
     * Non-blocking check for a possibility of a non-blocking operation on several channels.
     *
     * @param selector  A first channel to wait for (mandatory).
     * @param selectors Other channels to wait for.
     * @return true if one of pending operations wasn't blocking.
     */
    static boolean trySelect(Selector selector, Selector... selectors) throws InterruptedException {
        return trySelect(0, selector, selectors);
    }

    /**
     * Waits for a non-blocking action to be available.
     *
     * @param timeoutMs A timeout to wait for a non-blocking action to be available.
     * @param selector  A first channel to wait for (mandatory).
     * @param selectors Other channels to wait for.
     * @return true if one of pending operations wasn't blockingю
     */
    static boolean trySelect(long timeoutMs, Selector selector, Selector... selectors) throws InterruptedException {
        Semaphore sem = new Semaphore(0);

        ArrayList<Selector> sel = new ArrayList<>();
        Collections.addAll(sel, selectors);
        sel.add(selector);
        Collections.shuffle(sel);

        // Add waiters
        int i;
        for (i = 0; i < sel.size(); i++) {
            Selector s = sel.get(i);
            boolean blocking;
            if (s.isSender) {
                blocking = s.channel.ifFullAddWriterWaiter(sem);
            } else {
                blocking = s.channel.ifEmptyAddReaderWaiter(sem);
            }

            if (!blocking && s.sendRecv()) {
                s.afterAction();
                int j;
                for (j = 0; j <= i; j++) {
                    if (sel.get(j).isSender) {
                        sel.get(j).channel.delWriterWaiter(sem);
                    } else {
                        sel.get(j).channel.delReaderWaiter(sem);
                    }
                }
                return true;
            }
        }

        while (true) {
            // Re-checking all channels
            for (i = 0; i < sel.size(); i++) {
                Selector s = sel.get(i);
                if (s.isSender) {
                    int status = s.channel.hasFreeCapacityStatus();
                    if (status == AVAILABLE && s.sendRecv()) {
                        s.afterAction();
                        int j;
                        for (j = 0; j < sel.size(); j++) {
                            sel.get(j).channel.delWriterWaiter(sem);
                        }
                        return true;
                    } else if (status == CLOSED) {
                        return false;
                    }
                } else {
                    int status = s.channel.hasMessagesStatus();
                    if (status == AVAILABLE && s.sendRecv()) {
                        s.afterAction();
                        int j;
                        for (j = 0; j < sel.size(); j++) {
                            sel.get(j).channel.delReaderWaiter(sem);
                        }
                        return true;
                    } else if (status == CLOSED) {
                        return false;
                    }
                }
            }

            boolean success;
            if (timeoutMs == Long.MAX_VALUE) {
                sem.acquire();
                success = true;
            } else {
                success = sem.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            }

            if (!success) {
                int j;
                for (j = 0; j < sel.size(); j++) {
                    if (sel.get(j).isSender) {
                        sel.get(j).channel.delWriterWaiter(sem);
                    } else {
                        sel.get(j).channel.delReaderWaiter(sem);
                    }
                }
                return false;
            }
        }
    }
}
