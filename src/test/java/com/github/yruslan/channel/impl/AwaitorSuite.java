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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class AwaitorSuite {
    @Test
    public void immediateAwaitorShouldAlwaysReturnFalse() {
        Awaitor awaitor = new Awaitor(0L);
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();

        assertFalse(doAwait(awaitor, lock,cond));
    }

    @Test
    public void limitedTimeAwaitorsShouldFailOnTimeout() {
        Awaitor awaitor = new Awaitor(100L);
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();

        Instant start = Instant.now();
        boolean result = doAwait(awaitor, lock,cond);
        Instant finish = Instant.now();

        assertFalse(result);
        assertTrue(Duration.between(start, finish).toMillis() >= 100L);
    }

    @Test
    public void limitedTimeAwaitorsShouldReturnTrue() {
        Awaitor awaitor = new Awaitor(200L);
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            lock.lock();
            cond.signal();
            lock.unlock();
        });

        thread.start();

        Instant start = Instant.now();
        boolean result = doAwait(awaitor, lock,cond);
        Instant finish = Instant.now();

        assertTrue(result);
        assertTrue(Duration.between(start, finish).toMillis() < 200L);
    }

    @Test
    public void unlimitedTimeAwaitorsShouldReturnTrue() {
        Awaitor awaitor = new Awaitor();
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            lock.lock();
            cond.signal();
            lock.unlock();
        });

        thread.start();

        Instant start = Instant.now();
        boolean result = doAwait(awaitor, lock,cond);
        Instant finish = Instant.now();

        assertTrue(result);
        assertTrue(Duration.between(start, finish).toMillis() < 200L);
    }

    @Test
    public void unlimitedTimeUnfulfilledAwaitorShouldNeverReturn() {
        Awaitor awaitor = new Awaitor();
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();

        boolean[] result = new boolean[1];

        Thread thread = new Thread(() -> result[0] = doAwait(awaitor, lock,cond));
        thread.start();

        Instant start = Instant.now();
        try {
            synchronized (thread) {
                thread.wait(100L);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Instant finish = Instant.now();

        assertFalse(result[0]);
        assertTrue(Duration.between(start, finish).toMillis() >= 100L);
    }

    private boolean doAwait(Awaitor awaitor, ReentrantLock lock, Condition cond) {
        lock.lock();
        boolean isConditionMet = true;
        try {
            isConditionMet = awaitor.await(cond);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return isConditionMet;
    }

}
