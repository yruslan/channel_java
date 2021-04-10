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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelTest {
    @Test
    @DisplayName("work for asynchronous channels in a single threaded setup")
    public void asyncChannelsShouldWorkInSingleThread() throws InterruptedException {
        Channel<Integer> ch1 = Channel.make(1);

        ch1.send(1);
        int i = ch1.recv();

        assertEquals(i, 1);
    }

    @Test
    @DisplayName("async sent messages should arrive in FIFO order")
    public void asyncChannelsShouldPreserveOrder() throws InterruptedException {
        Channel<Integer> ch = Channel.make(5);

        ch.send(1);
        ch.send(2);
        ch.send(3);

        int v1 = ch.recv();

        ch.send(4);

        int v2 = ch.recv();
        int v3 = ch.recv();
        int v4 = ch.recv();

        assertEquals(v1, 1);
        assertEquals(v2, 2);
        assertEquals(v3, 3);
        assertEquals(v4, 4);
    }

    @Test
    @DisplayName("closed channel can still be used to receive pending messages")
    public void closedChannelCanReceivePendingMessages() throws InterruptedException {
        Channel<Integer> ch = Channel.make(3);

        ch.send(1);
        ch.send(2);
        ch.send(3);

        int v1 = ch.recv();
        ch.close();
        int v2 = ch.recv();
        int v3 = ch.recv();

        assertEquals(v1, 1);
        assertEquals(v2, 2);
        assertEquals(v3, 3);

        assertThrows(IllegalStateException.class, ch::recv);
    }

    @Test
    @DisplayName("closing a synchronous channel should block until the pending message is not received")
    public void closingSyncChannelShouldBlockIfMsgIsNotReceived() throws InterruptedException {
        Channel<Integer> ch = Channel.make();

        Thread thread = runInThread(() -> {
            Thread.sleep(10);
            ch.close();
        });

        ch.send(1);

        thread.join(50);
        assertTrue(thread.isAlive());

        thread.interrupt();
    }

    @Test
    @DisplayName("closing a synchronous channel should block until the pending message is not received")
    public void closingSyncChannelShouldBlockUntilMsgIsReceived() throws InterruptedException {
        Channel<Integer> ch = Channel.make();
        ArrayList<Integer> v = new ArrayList<>();
        Instant start = Instant.now();

        runInThread(() -> {
            Thread.sleep(120);
            synchronized (v) {
                v.add(ch.recv());
            }
        });

        runInThread(() ->
                ch.send(1)
        );

        Thread thread3 = runInThread(() -> {
            Thread.sleep(50);
            ch.close();
        });

        thread3.join(2000);
        Instant finish = Instant.now();

        assertTrue(v.size() > 0);
        assertTrue(v.contains(1));
        assertTrue(Duration.between(start, finish).toMillis() > 60);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("reading a closed channel should throw an exception")
    public void readingClosedChannelThrows() throws InterruptedException {
        Channel<Integer> ch = Channel.make(5);

        ch.send(1);

        Integer v = ch.recv();
        ch.close();

        assertEquals((int) v, 1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, ch::recv);
        assertTrue(ex.getMessage().contains("Attempt to receive from a closed channel"));
    }

    @Test
    @DisplayName("sending to a closed channel should throw an exception")
    public void sendingToClosedChannelThrows() throws InterruptedException {
        Channel<Integer> ch = Channel.make(2);

        boolean ok = ch.trySend(1);
        assertTrue(ok);

        ch.close();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ch.send(2));

        assertTrue(ex.getMessage().contains("Attempt to send to a closed channel"));

        Optional<Integer> v1 = ch.tryRecv();
        Optional<Integer> v2 = ch.tryRecv();

        assertTrue(v1.isPresent());
        assertEquals((int) v1.get(), 1);
        assertFalse(v2.isPresent());
    }

    @Test
    @DisplayName("sync send/recv should block")
    public void syncSendRecvShouldBlock() throws InterruptedException {
        Channel<Integer> ch = Channel.make();
        ArrayList<Integer> v = new ArrayList<>();
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(100);
            v.add(ch.recv());
        });

        ch.send(100);

        thread.join(2000);
        Instant finish = Instant.now();

        assertTrue(v.size() > 0);
        assertEquals((int) v.get(0), 100);
        assertTrue(Duration.between(start, finish).toMillis() >= 100);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for sync channels handle non-blocking way when data is available")
    public void trySendSyncNonBlockingDataAvailable() throws InterruptedException {
        Channel<String> ch = Channel.make();

        runInThread(ch::recv);

        Thread.sleep(30);

        boolean ok = ch.trySend("test", 0);

        assertTrue(ok);
    }

    @Test
    @DisplayName("trySend() for sync channels handle non-blocking way when data is not available")
    public void trySendSyncNonBlockingDataNotAvailable() throws InterruptedException {
        Channel<String> ch = Channel.make();

        //ch.trySend("test1", 0);
        boolean ok = ch.trySend("test2", 0);

        assertFalse(ok);
    }

    @Test
    @DisplayName("trySend() for sync channels should return false on a closed channel")
    public void trySendSyncNonBlockingDataClosedChannel() throws InterruptedException {
        Channel<Integer> ch = Channel.make();
        ch.close();

        boolean ok = ch.trySend(2);

        assertFalse(ok);
    }

    @Test
    @DisplayName("trySend() for sync channels should handle finite timeouts when timeout is not expired")
    public void trySendSyncHandleFiniteTimeoutsNotExpired() throws InterruptedException {
        Channel<String> ch = Channel.make();
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(10);
            ch.recv();
        });

        boolean ok = ch.trySend("test", 200);
        thread.join(2000);
        Instant finish = Instant.now();

        assertTrue(ok);
        assertTrue(Duration.between(start, finish).toMillis() >= 10);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for sync channels should handle finite timeouts when timeout is expired")
    public void trySendSyncHandleFiniteTimeoutsExpired() throws InterruptedException {
        Channel<String> ch = Channel.make();
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(100);
            ch.recv();
        });

        boolean ok = ch.trySend("test", 10);
        ch.send("test1");

        thread.join(2000);
        Instant finish = Instant.now();

        assertFalse(ok);
        assertTrue(Duration.between(start, finish).toMillis() >= 10);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for sync channels should handle infinite timeouts when data is ready")
    public void trySendSyncHandleInfiniteTimeoutsDataReady() throws InterruptedException {
        Channel<String> ch = Channel.make();
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(10);
            ch.recv();
        });

        boolean ok = ch.trySend("test", Long.MAX_VALUE);

        thread.join(2000);
        Instant finish = Instant.now();

        assertTrue(ok);
        assertTrue(Duration.between(start, finish).toMillis() >= 10);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for sync channels should handle infinite timeouts when data is not ready")
    public void trySendSyncHandleInfiniteTimeoutsDataNotReady() throws InterruptedException {
        Channel<String> ch = Channel.make();
        Instant start = Instant.now();

        Thread thread = runInThread(() -> ch.trySend("test", Long.MAX_VALUE));

        thread.join(50);
        Instant finish = Instant.now();

        assertTrue(thread.isAlive());
        assertTrue(Duration.between(start, finish).toMillis() >= 50);
        thread.interrupt();
    }

    @Test
    @DisplayName("trySend() for async channels handle non-blocking way when data is available")
    public void trySendAsyncNonBlockingDataAvailable() throws InterruptedException {
        Channel<String> ch = Channel.make(1);

        boolean ok = ch.trySend("test", 0);

        assertTrue(ok);
    }

    @Test
    @DisplayName("trySend() for async channels handle non-blocking way when data is not available")
    public void trySendAsyncNonBlockingDataNotAvailable() throws InterruptedException {
        Channel<String> ch = Channel.make(1);

        boolean ok1 = ch.trySend("test1", 0);
        boolean ok2 = ch.trySend("test2", 0);

        assertTrue(ok1);
        assertFalse(ok2);
    }

    @Test
    @DisplayName("trySend() for async channels should return false on a closed channel")
    public void trySendAsyncNonBlockingDataClosedChannel() throws InterruptedException {
        Channel<Integer> ch = Channel.make(1);
        ch.close();

        boolean ok = ch.trySend(2);

        assertFalse(ok);
    }

    @Test
    @DisplayName("trySend() for async channels should handle finite timeouts when timeout is not expired")
    public void trySendAsyncHandleFiniteTimeoutsNotExpired() throws InterruptedException {
        Channel<String> ch = Channel.make(1);
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(10);
            ch.recv();
        });

        boolean ok1 = ch.trySend("test1", 0);
        boolean ok2 = ch.trySend("test2", 200);
        thread.join(2000);
        Instant finish = Instant.now();

        assertTrue(ok1);
        assertTrue(ok2);
        assertTrue(Duration.between(start, finish).toMillis() >= 10);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for async channels should handle finite timeouts when timeout is expired")
    public void trySendAsyncHandleFiniteTimeoutsExpired() throws InterruptedException {
        Channel<String> ch = Channel.make(1);
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(100);
            ch.recv();
        });

        boolean ok1 = ch.trySend("test1", 0);
        boolean ok2 = ch.trySend("test2", 10);

        thread.join(2000);
        Instant finish = Instant.now();

        assertTrue(ok1);
        assertFalse(ok2);
        assertTrue(Duration.between(start, finish).toMillis() >= 10);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for async channels should handle infinite timeouts when data is ready")
    public void trySendAsyncHandleInfiniteTimeoutsDataReady() throws InterruptedException {
        Channel<String> ch = Channel.make(1);
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            Thread.sleep(10);
            ch.recv();
        });

        boolean ok1 = ch.trySend("test1", 0);
        boolean ok2 = ch.trySend("test2", Long.MAX_VALUE);

        thread.join(2000);
        Instant finish = Instant.now();

        assertTrue(ok1);
        assertTrue(ok2);
        assertTrue(Duration.between(start, finish).toMillis() >= 10);
        assertTrue(Duration.between(start, finish).toMillis() < 2000);
    }

    @Test
    @DisplayName("trySend() for async channels should handle infinite timeouts when data is not ready")
    public void trySendAsyncHandleInfiniteTimeoutsDataNotReady() throws InterruptedException {
        Channel<String> ch = Channel.make(1);
        Instant start = Instant.now();

        Thread thread = runInThread(() -> {
            ch.trySend("test1", 0);
            ch.trySend("test2", Long.MAX_VALUE);
        });

        thread.join(50);
        Instant finish = Instant.now();

        assertTrue(thread.isAlive());
        assertTrue(Duration.between(start, finish).toMillis() >= 50);
        thread.interrupt();
    }

    private Thread runInThread(Interruptable f) {
        Thread thread = new Thread(() -> {
            try {
                f.run();
            } catch (InterruptedException ignored) {
            }
        });
        thread.start();
        return thread;
    }


}
