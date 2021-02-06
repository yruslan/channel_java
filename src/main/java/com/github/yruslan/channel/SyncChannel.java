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

import java.util.Optional;
import java.util.concurrent.Semaphore;

public class SyncChannel<T> extends Channel<T> {
    protected Optional<T> syncValue = Optional.empty();

    @Override
    final public void close() throws InterruptedException {
        lock.lock();
        try {
            if (!closed) {
                closed = true;
                readWaiters.foreach(Semaphore::release);
                writeWaiters.foreach(Semaphore::release);
                crd.signalAll();
                cwr.signalAll();

                writers += 1;
                while (!syncValue.isPresent()) {
                    cwr.await();
                }
                writers -= 1;
            }
        } catch (InterruptedException e) {
            writers -= 1;
            throw e;
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
            while (!syncValue.isPresent() && !closed) {
                cwr.await();
            }
            if (!closed) {
                syncValue = Optional.of(value);
                notifyReaders();

                while (!syncValue.isPresent() && !closed) {
                    cwr.await();
                }
                notifyWriters();
            }
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
            if (closed || !hasCapacity()) {
                return false;
            }
            syncValue = Optional.of(value);
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
            if (syncValue.isPresent()) {
                isSucceeded = false;
            } else {
                if (!hasCapacity()){
                    isSucceeded = false;
                } else {
                    syncValue = Optional.of(value);
                    notifyReaders();
                    isSucceeded = true;
                }
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
            if (!closed && !syncValue.isPresent()) {
                notifyWriters();
            }
            while (!closed && !syncValue.isPresent()) {
                crd.await();
            }

            if (closed && !syncValue.isPresent()) {
                throw new IllegalStateException("Attempt to receive from a closed channel.");
            }

            T v = syncValue.get();
            syncValue = Optional.empty();
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
            if (closed && !syncValue.isPresent()) {
                return Optional.empty();
            }
            if (!syncValue.isPresent()) {
                return Optional.empty();
            }
            Optional<T> v = syncValue;
            syncValue = Optional.empty();
            notifyWriters();
            return v;
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
        if (syncValue.isPresent()) {
            result = false;
        } else {
            result = closed;
        }
        lock.unlock();
        return result;
    }

    @Override
    final protected boolean hasMessages() {
        return syncValue.isPresent();
    }

    @Override
    final protected boolean hasCapacity() {
        return !syncValue.isPresent() && (readers > 0 || readWaiters.nonEmpty());
    }

    @Override
    final protected Optional<T> fetchValueOpt() {
        if (syncValue.isPresent()) {
            notifyReaders();
        }
        Optional<T> v = syncValue;
        syncValue = Optional.empty();
        return v;
    }
}
