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

import static org.junit.jupiter.api.Assertions.*;

public class ChannelSuite {
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

        Thread thread = new Thread(() -> {
            try {
                ch.close();
            } catch (InterruptedException ignored) {
            }
        });
        thread.start();

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
