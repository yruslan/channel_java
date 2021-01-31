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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public final class Awaitor {
    private final long timeoutMilli;
    private final Instant startInstant;

    Awaitor() {
        timeoutMilli = Long.MAX_VALUE;
        startInstant = Instant.now();
    }

    Awaitor(long timeoutMilli) {
        this.timeoutMilli = timeoutMilli;
        startInstant = Instant.now();
    }

    boolean await(Condition cond) throws InterruptedException {
        if (timeoutMilli == 0L) {
            return false;
        }
        if (timeoutMilli == Long.MAX_VALUE) {
            cond.await();
        } else {
            cond.await(timeLeft(), TimeUnit.MILLISECONDS);
        }
        return !isTimeoutExpired();
    }

    private boolean isTimeoutExpired() {
        if (timeoutMilli == Long.MAX_VALUE) {
            return false;
        } else {
            return elapsedTime() >= timeoutMilli;
        }
    }

    private long elapsedTime() {
        Instant now = Instant.now();
        return Duration.between(startInstant, now).toMillis();
    }

    private long timeLeft() {
        long timeLeft = timeoutMilli - elapsedTime();
        return Math.max(timeLeft, 0L);
    }

}
