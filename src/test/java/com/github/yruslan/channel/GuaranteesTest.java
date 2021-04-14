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

import com.sun.tools.javac.util.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static com.github.yruslan.channel.Channel.make;
import static com.github.yruslan.channel.Channel.select;
import static com.github.yruslan.channel.utils.Utils.runInThread;
import static com.github.yruslan.channel.utils.Utils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class GuaranteesTest {
    @Test
    @DisplayName("Progress guarantees are provided when one channel always has messages to process")
    public void processGuaranteesAreProvidedWhenOneChannelAlwaysHasMsgs() throws InterruptedException {
        // Here we test that when
        // * There a worker processing messages from 2 channels and
        // * Processing a message takes more time than a new message to arrive in each channel
        // Then
        // * Messages from both channels have a chance to be processed, none of the channels
        //   gets priority.
        Channel<Integer> ch1 = make(20);
        Channel<Integer> ch2 = make(20);

        ArrayList<Instant> processed1Times = new ArrayList<>();
        ArrayList<Instant> processed2Times = new ArrayList<>();

        Thread thread = runInThread(() -> {
            while (!ch1.isClosed() && !ch2.isClosed()) {
                select(
                        ch1.recver((i) -> {
                            processed1Times.add(Instant.now());
                            sleep(30);
                        }),
                        ch2.recver((i) -> {
                            processed2Times.add(Instant.now());
                            sleep(30);
                        })
                );
            }
        });

        for (int i = 1; i < 20; i++) {
            ch1.send(1);
            ch2.send(2);

            sleep(20);
        }

        ch1.close();
        ch2.close();

        thread.join(2000);

        // At least one message from ch1 and ch2 should be processed
        assertFalse(processed1Times.isEmpty());
        assertFalse(processed2Times.isEmpty());
        assertFalse(thread.isAlive());
    }

    @Test
    @DisplayName("Fairness for sync channels is provided when several channels are active, a channel is selected randomly")
    public void fairnessSyncIsProvidedWhenSeveralChannelsAreActive() throws InterruptedException {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Channel<Integer> in1 = make();
        Channel<Integer> in2 = make();

        Channel<Integer> out1 = make();
        Channel<Integer> out2 = make();
        Channel<Boolean> finish = make();

        testFairness(in1, in2, out1, out2, finish);
    }

    @Test
    @DisplayName("Fairness for async channels is provided when several channels are active, a channel is selected randomly")
    public void fairnessAsyncIsProvidedWhenSeveralChannelsAreActive() throws InterruptedException {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Channel<Integer> in1 = make(1);
        Channel<Integer> in2 = make(1);

        Channel<Integer> out1 = make(1);
        Channel<Integer> out2 = make(1);
        Channel<Boolean> finish = make();

        testFairness(in1, in2, out1, out2, finish);
    }

    @Test
    @DisplayName("Fairness for sync/async 1 channels is provided when several channels are active, a channel is selected randomly")
    public void fairnessMix1IsProvidedWhenSeveralChannelsAreActive() throws InterruptedException {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Channel<Integer> in1 = make(1);
        Channel<Integer> in2 = make(1);

        Channel<Integer> out1 = make();
        Channel<Integer> out2 = make();
        Channel<Boolean> finish = make();

        testFairness(in1, in2, out1, out2, finish);
    }

    @Test
    @DisplayName("Fairness for sync/async 2 channels is provided when several channels are active, a channel is selected randomly")
    public void fairnessMix2IsProvidedWhenSeveralChannelsAreActive() throws InterruptedException {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Channel<Integer> in1 = make();
        Channel<Integer> in2 = make();

        Channel<Integer> out1 = make(1);
        Channel<Integer> out2 = make(1);
        Channel<Boolean> finish = make();

        testFairness(in1, in2, out1, out2, finish);
    }

    private void testFairness(Channel<Integer> in1, Channel<Integer> in2, Channel<Integer> out1, Channel<Integer> out2, Channel<Boolean> finish) throws InterruptedException {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Thread[] workers = new Thread[4];

        // Launching workers
        for (int i = 0; i < 4; i++) {
            final int ii = i;
            if (i % 2 == 0) {
                workers[i] = runInThread(() -> worker(ii, out1, results));
            } else {
                workers[i] = runInThread(() -> worker(ii, out2, results));
            }
        }

        // Launching the load balancer
        Thread threadBalancer = runInThread(() -> balancer(in1, in2, out1, out2, finish));

        // Sending out work
        for (int i = 1; i < 101; i++) {
            if (i % 2 == 0) {
                in1.send(i);
            } else {
                in2.send(i);
            }
            Thread.sleep(10);
        }

        // Letting the balancer and the worker threads that the processing is finished
        finish.send(true);
        out1.close();
        out2.close();

        // Waiting for the threads to finish
        for (Thread w : workers) {
            w.join();
        }
        threadBalancer.join();

        // Correctness
        int sum = results.stream().map((p) -> p.snd).reduce(0, Integer::sum);
        assertEquals(results.size(), 100);
        assertEquals(sum, 10100);

        // Fairness
        int[] processedBy = new int[4];
        for (int i = 0; i < 4; i++) {
            processedBy[i] = 0;
        }
        for (Pair<Integer, Integer> result : results) {
            processedBy[result.fst] += 1;
        }
        assertTrue(Arrays.stream(processedBy).min().getAsInt() > 15);
        assertTrue(Arrays.stream(processedBy).max().getAsInt() < 35);
    }

    private void balancer(ReadChannel<Integer> input1,
                          ReadChannel<Integer> input2,
                          WriteChannel<Integer> output1,
                          WriteChannel<Integer> output2,
                          ReadChannel<Boolean> finishChannel) throws InterruptedException {
        Integer[] v = new Integer[1];
        Boolean[] exit = new Boolean[1];

        v[0] = 0;
        exit[0] = false;

        while (!exit[0]) {
            select(
                    input1.recver((x) -> v[0] = x),
                    input2.recver((x) -> v[0] = x),
                    finishChannel.recver((z) -> exit[0] = true)
            );

            if (!exit[0]) {
                select(
                        output1.sender(v[0], () -> {
                        }),
                        output2.sender(v[0], () -> {
                        })
                );
            }
        }
    }

    private void worker(int num, ReadChannel<Integer> input1, ArrayList<Pair<Integer, Integer>> results) throws InterruptedException {
        Random rand = new Random();

        input1.foreach((x) -> {
            sleep(rand.nextInt(5) + 10);
            synchronized (results) {
                results.add(Pair.of(num, 2 * x));
            }
        });
    }

}
