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

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleLinkedListSuite {

    @Test
    public void initializeAnEmptyList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        assertTrue(lst.isEmpty());
        assertEquals(lst.size(), 0);
    }

    @Test
    public void appendShouldAddAnElement() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 1);
        assertEquals(lst.head(), 1);
    }

    @Test
    public void appendShouldAddSeveralElements() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 3);
        assertEquals(lst.head(), 1);
    }

    @Test
    void removeShouldRemoveASingleElement() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.remove(1);

        assertTrue(lst.isEmpty());
        assertEquals(lst.size(), 0);
    }

    @Test
    void removeAnElementAtTheBeginning() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);
        lst.remove(1);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 2);
        assertEquals(lst.head(), 2);
    }

    @Test
    void removeAnElementAtTheMiddle() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);
        lst.remove(2);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 2);
        assertEquals(lst.head(), 1);
    }

    @Test
    void removeAnElementAtTheEnd() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);
        lst.remove(3);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 2);
        assertEquals(lst.head(), 1);
    }

    @Test
    void removeTwoElements() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        lst.remove(3);
        assertEquals(lst.head(), 1);

        lst.remove(1);
        assertEquals(lst.head(), 2);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 1);
    }

    @Test
    void removeAllElements() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        lst.remove(2);
        lst.remove(3);
        lst.remove(1);

        assertTrue(lst.isEmpty());
        assertEquals(lst.size(), 0);
    }

    @Test
    void dontRemoveElementThatDoesNotExist() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        lst.remove(4);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 3);
    }

    @Test
    void doNothingtoTheEmptyList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.remove(1);

        assertTrue(lst.isEmpty());
        assertEquals(lst.size(), 0);
    }

    @Test
    void clearShouldEmptyTheList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        lst.clear();

        assertTrue(lst.isEmpty());
        assertEquals(lst.size(), 0);
    }

    @Test
    void foreachShouldProcessNothingForAnEmptyList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        AtomicInteger i = new AtomicInteger();
        lst.foreach(j -> i.addAndGet(1));

        assertEquals(i.get(), 0);
    }

    @Test
    void foreachShouldProcessAllElements() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        AtomicInteger i = new AtomicInteger();
        lst.foreach(i::addAndGet);

        assertEquals(i.get(), 6);
    }

    @Test
    void headShouldReturnHeadElementIfOneInTheList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(2);

        assertFalse(lst.isEmpty());

        assertEquals(lst.head(), 2);
        assertEquals(lst.head(), 2);
        assertEquals(lst.size(), 1);
    }

    @Test
    void headShouldReturFirstElementOfTheList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(4);
        lst.append(5);
        lst.append(6);

        assertFalse(lst.isEmpty());

        assertEquals(lst.head(), 4);
        assertEquals(lst.head(), 4);
        assertEquals(lst.size(), 3);
    }

    @Test
    void headShouldThrowAnExceptionOnEmptyList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        assertThrows(NoSuchElementException.class, lst::head);
    }

    @Test
    void returnHeadAndRotateShouldReturnTheOnlyElement() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);

        assertEquals(lst.returnHeadAndRotate(), 1);
        assertEquals(lst.returnHeadAndRotate(), 1);
        assertEquals(lst.returnHeadAndRotate(), 1);
    }

    @Test
    void returnHeadAndRotateShouldReturnAndRotate() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        lst.append(1);
        lst.append(2);
        lst.append(3);

        // "The definition of insanity is doing the same thing over and over again, but expecting different results."
        //                                                                                    --  Albert Einstein
        assertEquals(lst.returnHeadAndRotate(), 1);
        assertEquals(lst.returnHeadAndRotate(), 2);
        assertEquals(lst.returnHeadAndRotate(), 3);
        assertEquals(lst.returnHeadAndRotate(), 1);
        assertEquals(lst.returnHeadAndRotate(), 2);
        assertEquals(lst.returnHeadAndRotate(), 3);

        assertFalse(lst.isEmpty());
        assertEquals(lst.size(), 3);
    }

    @Test
    void returnHeadAndRotateShouldThrowOnEmptyList() {
        SimpleLinkedList<Integer> lst = new SimpleLinkedList<>();

        assertThrows(NoSuchElementException.class, lst::returnHeadAndRotate);
    }

}
