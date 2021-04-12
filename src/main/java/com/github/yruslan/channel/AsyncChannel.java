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

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class AsyncChannel<T> extends Channel<T> {
    protected int maxCapacity;
    protected Queue<T> q;

    AsyncChannel(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.q = new ArrayDeque<>();
    }

    @Override
    final public void close() {
        lock.lock();
        try {
            if (!closed) {
                closed = true;
                readWaiters.foreach(Semaphore::release);
                writeWaiters.foreach(Semaphore::release);
                crd.signalAll();
                cwr.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public void send(T value) throws InterruptedException {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Attempt to send to a closed channel.");
            }

            writers += 1;
            while (q.size() == maxCapacity && !closed) {
                cwr.await();
            }
            if (!closed) {
                q.add(value);
            }
            notifyReaders();
            writers -= 1;
        } catch (InterruptedException e) {
            writers -= 1;
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public boolean trySend(T value) {
        lock.lock();
        try {
            if (closed || q.size() == maxCapacity) {
                return false;
            }
            q.add(value);
            notifyReaders();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public boolean trySend(T value, long timeoutMs) throws InterruptedException {
        if (timeoutMs == 0) {
            return trySend(value);
        }

        Awaiter awaiter = new Awaiter(timeoutMs);

        lock.lock();
        try {
            writers += 1;
            boolean isTimeoutExpired = false;

            while (!closed && !hasCapacity() && !isTimeoutExpired) {
                isTimeoutExpired = !awaiter.await(cwr);
            }
            boolean isSucceeded;
            if (!closed && hasCapacity()) {
                isSucceeded = true;
                q.add(value);
                notifyReaders();
            } else {
                isSucceeded = false;
            }
            writers -= 1;
            return isSucceeded;
        } catch (InterruptedException e) {
            writers -= 1;
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public T recv() throws InterruptedException {
        lock.lock();
        try {
            readers += 1;
            while (!closed && q.isEmpty()) {
                crd.await();
            }

            if (closed && q.isEmpty()) {
                throw new IllegalStateException("Attempt to receive from a closed channel.");
            }

            T v = q.poll();
            readers -= 1;
            notifyWriters();
            return v;
        } catch (InterruptedException e) {
            readers -= 1;
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public Optional<T> tryRecv() {
        lock.lock();
        try {
            if (closed && q.isEmpty()) {
                return Optional.empty();
            }
            if (q.isEmpty()) {
                return Optional.empty();
            }
            notifyWriters();
            return Optional.ofNullable(q.poll());
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public Optional<T> tryRecv(long timeoutMs) throws InterruptedException {
        if (timeoutMs == 0) {
            return tryRecv();
        }

        Awaiter awaiter = new Awaiter(timeoutMs);

        lock.lock();
        try {
            readers += 1;
            boolean isTimeoutExpired = false;
            while (!closed && !hasMessages() && !isTimeoutExpired) {
                isTimeoutExpired = !awaiter.await(crd);
            }
            readers -= 1;
            return fetchValueOpt();
        } catch (InterruptedException e) {
            readers -= 1;
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    final public boolean isClosed() {
        lock.lock();
        boolean result;
        if (!q.isEmpty()) {
            result = false;
        } else {
            result = closed;
        }
        lock.unlock();
        return result;
    }

    @Override
    final protected boolean hasMessages() {
        return !q.isEmpty();
    }

    @Override
    final protected boolean hasCapacity() {
        return q.size() < maxCapacity;
    }

    @Override
    final protected Optional<T> fetchValueOpt() {
        return Optional.ofNullable(q.poll());
    }
}
